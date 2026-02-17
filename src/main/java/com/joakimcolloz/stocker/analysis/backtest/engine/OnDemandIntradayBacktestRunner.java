package com.joakimcolloz.stocker.analysis.backtest.engine;

import com.joakimcolloz.stocker.analysis.backtest.dto.ExecutionConfig;
import com.joakimcolloz.stocker.analysis.backtest.dto.EntryRule;
import com.joakimcolloz.stocker.analysis.backtest.dto.ExitRule;
import com.joakimcolloz.stocker.analysis.backtest.dto.Trade;
import com.joakimcolloz.stocker.analysis.backtest.execution.ConditionEvaluator;
import com.joakimcolloz.stocker.analysis.backtest.execution.CostModel;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterRequest;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import com.joakimcolloz.stocker.api.entity.IntradayCandlestick;

import java.util.ArrayList;
import java.util.List;

final class OnDemandIntradayBacktestRunner {

    private static final long SECONDS_PER_DAY = 86_400L;
    private static final long INTRADAY_BAR_SECONDS = 300L;

    private final BacktestEngine engine;

    OnDemandIntradayBacktestRunner(BacktestEngine engine) {
        this.engine = engine;
    }

    List<Trade> run(
            String symbol,
            List<Candlestick> sortedCandles,
            long tradingStartTimestamp,
            List<FilterRequest> entryConditions,
            ExitRule exitRule,
            List<FilterRequest> exitConditions,
            IndicatorContext indicators,
            EntryRule entryRule,
            ExecutionConfig executionConfig,
            BacktestEngine.IntradayCandlesProvider intradayCandlesProvider,
            CostModel costModel
    ) {
        List<Trade> trades = new ArrayList<>();
        RunState state = new RunState();

        EntryParams entryParams = entryParams(entryRule);
        boolean useIntraday = executionConfig.mode() == com.joakimcolloz.stocker.analysis.backtest.dto.ExecutionMode.HYBRID_INTRADAY
                && intradayCandlesProvider != null;

        for (int i = 0; i < sortedCandles.size(); i++) {
            Candlestick bar = sortedCandles.get(i);
            Candlestick previousBar = i > 0 ? sortedCandles.get(i - 1) : null;
            IntradayDayAccess intradayAccess = new IntradayDayAccess(useIntraday, intradayCandlesProvider, bar.getTimestamp());

            if (state.position != null) {
                Trade closed = evaluateExitForDay(state, bar, previousBar, exitRule, exitConditions, indicators,
                        executionConfig, costModel, intradayAccess);
                if (closed != null) {
                    trades.add(closed);
                    state.position = null;
                    state.highWaterMark = 0.0;
                } else {
                    updateTrailingHighWaterMarkIfNeeded(state, bar, exitRule);
                }
            }

            if (state.pendingOrder != null && state.position == null) {
                BacktestEngine.Position entered = tryFillPendingOrder(symbol, bar, entryParams, state.pendingOrder, costModel, intradayAccess);
                if (entered != null) {
                    state.position = entered;
                    state.pendingOrder = null;
                    state.highWaterMark = entered.entryPrice();

                    Trade closed = evaluateExitAfterSameDayEntry(state, bar, previousBar, exitRule, exitConditions, indicators,
                            executionConfig, costModel, intradayAccess);
                    if (closed != null) {
                        trades.add(closed);
                        state.position = null;
                        state.highWaterMark = 0.0;
                    } else {
                        updateTrailingHighWaterMarkIfNeeded(state, bar, exitRule);
                    }
                } else {
                    state.pendingOrder.barsRemaining--;
                    if (state.pendingOrder.barsRemaining <= 0) state.pendingOrder = null;
                }
            }

            if (state.position == null && state.pendingOrder == null && bar.getTimestamp() >= tradingStartTimestamp) {
                if (ConditionEvaluator.allEntryConditionsMet(bar, previousBar, entryConditions, indicators)) {
                    state.pendingOrder = createPendingOrderFromSignal(bar, entryParams);
                }
            }
        }

        if (state.position != null && !sortedCandles.isEmpty()) {
            Candlestick lastBar = sortedCandles.getLast();
            trades.add(engine.closeTrade(state.position, lastBar.getTimestamp(), lastBar.getClose(), "END_OF_DATA", costModel));
        }

        return trades;
    }

    private static final class RunState {
        BacktestEngine.Position position;
        PendingOrder pendingOrder;
        double highWaterMark;
    }

    private record EntryParams(
            String entryMode,
            double limitOffsetPercent,
            double penetrationPercent,
            int maxBarsToFill,
            String direction
    ) {
    }

    private EntryParams entryParams(EntryRule entryRule) {
        String entryMode = entryRule.entryMode();
        double limitOffsetPercent = entryRule.limitOffsetPercent() != null ? entryRule.limitOffsetPercent() : 0.0;
        double penetrationPercent = entryRule.penetrationPercent() != null ? entryRule.penetrationPercent() : 0.0;
        int maxBarsToFill = entryRule.maxBarsToFill() != null ? entryRule.maxBarsToFill() : 1;
        String direction = entryRule.limitDirection() != null ? entryRule.limitDirection() : "BELOW";
        return new EntryParams(entryMode, limitOffsetPercent, penetrationPercent, maxBarsToFill, direction);
    }

    private static final class PendingOrder {
        final double limitPrice;
        final String direction;
        int barsRemaining;

        private PendingOrder(double limitPrice, int barsRemaining, String direction) {
            this.limitPrice = limitPrice;
            this.barsRemaining = barsRemaining;
            this.direction = direction;
        }
    }

    private static final class IntradayDayAccess {
        private final boolean useIntraday;
        private final BacktestEngine.IntradayCandlesProvider provider;
        private final long dayStart;
        private List<IntradayCandlestick> cached;

        private IntradayDayAccess(boolean useIntraday, BacktestEngine.IntradayCandlesProvider provider, long dayStart) {
            this.useIntraday = useIntraday;
            this.provider = provider;
            this.dayStart = dayStart;
        }

        private List<IntradayCandlestick> bars() {
            if (!useIntraday) {
                return List.of();
            }
            if (cached == null) {
                cached = provider.fetch(dayStart, dayStart + SECONDS_PER_DAY);
                if (cached == null) {
                    cached = List.of();
                }
            }
            return cached;
        }

        private boolean looksUsable() {
            List<IntradayCandlestick> bars = bars();
            return !bars.isEmpty() && bars.getFirst().getTimestamp() <= (dayStart + INTRADAY_BAR_SECONDS);
        }
    }

    private Trade evaluateExitForDay(
            RunState state,
            Candlestick bar,
            Candlestick previousBar,
            ExitRule exitRule,
            List<FilterRequest> exitConditions,
            IndicatorContext indicators,
            ExecutionConfig executionConfig,
            CostModel costModel,
            IntradayDayAccess intradayAccess
    ) {
        if (intradayAccess.looksUsable()) {
            BacktestEngine.ExitCheckResult intradayExit = engine.checkExitIntraday(
                    state.position,
                    intradayAccess.bars(),
                    exitRule,
                    state.highWaterMark,
                    executionConfig,
                    costModel
            );
            state.highWaterMark = intradayExit.updatedHighWaterMark();
            return intradayExit.trade();
        }

        return engine.checkExitDaily(state.position, bar, previousBar, exitRule, exitConditions, indicators,
                state.highWaterMark, state.highWaterMark, executionConfig, costModel);
    }

    private void updateTrailingHighWaterMarkIfNeeded(RunState state, Candlestick bar, ExitRule exitRule) {
        if (Boolean.TRUE.equals(exitRule.trailingStopLoss())) {
            state.highWaterMark = Math.max(state.highWaterMark, bar.getClose());
        }
    }

    private BacktestEngine.Position tryFillPendingOrder(
            String symbol,
            Candlestick dailyBar,
            EntryParams entryParams,
            PendingOrder pendingOrder,
            CostModel costModel,
            IntradayDayAccess intradayAccess
    ) {
        if ("NEXT_BAR_OPEN".equals(entryParams.entryMode())) {
            long entryTs = dailyBar.getTimestamp();
            double rawEntryPrice = dailyBar.getOpen();
            if (intradayAccess.looksUsable()) {
                IntradayCandlestick first = intradayAccess.bars().getFirst();
                entryTs = first.getTimestamp();
                rawEntryPrice = first.getOpen();
            }
            double entryFill = costModel.applyEntryPrice(rawEntryPrice);
            return new BacktestEngine.Position(symbol, entryTs, entryFill);
        }

        IntradayEntryFill intradayFill = intradayAccess.looksUsable()
                ? tryFillLimitIntraday(pendingOrder, entryParams.penetrationPercent(), intradayAccess.bars())
                : null;

        if (intradayFill != null) {
            double entryFill = costModel.applyEntryPrice(intradayFill.rawEntryPrice);
            return new BacktestEngine.Position(symbol, intradayFill.entryTimestamp, entryFill);
        }

        DailyEntryFill dailyFill = tryFillLimitDaily(pendingOrder, entryParams.penetrationPercent(), dailyBar);
        if (dailyFill == null) {
            return null;
        }

        double entryFill = costModel.applyEntryPrice(dailyFill.rawEntryPrice);
        return new BacktestEngine.Position(symbol, dailyFill.entryTimestamp, entryFill);
    }

    private record IntradayEntryFill(long entryTimestamp, double rawEntryPrice) {
    }

    private record DailyEntryFill(long entryTimestamp, double rawEntryPrice) {
    }

    private IntradayEntryFill tryFillLimitIntraday(PendingOrder pendingOrder, double penetrationPercent, List<IntradayCandlestick> dayBars) {
        if ("ABOVE".equals(pendingOrder.direction)) {
            double fillThreshold = pendingOrder.limitPrice * (1.0 + penetrationPercent / 100.0);
            for (IntradayCandlestick iBar : dayBars) {
                if (iBar.getHigh() >= fillThreshold) {
                    double rawEntryPrice = iBar.getOpen() >= fillThreshold ? iBar.getOpen() : pendingOrder.limitPrice;
                    return new IntradayEntryFill(iBar.getTimestamp(), rawEntryPrice);
                }
            }
            return null;
        }

        double fillThreshold = pendingOrder.limitPrice * (1.0 - penetrationPercent / 100.0);
        for (IntradayCandlestick iBar : dayBars) {
            if (iBar.getLow() <= fillThreshold) {
                return new IntradayEntryFill(iBar.getTimestamp(), pendingOrder.limitPrice);
            }
        }
        return null;
    }

    private DailyEntryFill tryFillLimitDaily(PendingOrder pendingOrder, double penetrationPercent, Candlestick bar) {
        if ("ABOVE".equals(pendingOrder.direction)) {
            double fillThreshold = pendingOrder.limitPrice * (1.0 + penetrationPercent / 100.0);
            if (bar.getHigh() >= fillThreshold) {
                return new DailyEntryFill(bar.getTimestamp(), pendingOrder.limitPrice);
            }
            return null;
        }

        double fillThreshold = pendingOrder.limitPrice * (1.0 - penetrationPercent / 100.0);
        if (bar.getLow() <= fillThreshold) {
            return new DailyEntryFill(bar.getTimestamp(), pendingOrder.limitPrice);
        }
        return null;
    }

    private Trade evaluateExitAfterSameDayEntry(
            RunState state,
            Candlestick bar,
            Candlestick previousBar,
            ExitRule exitRule,
            List<FilterRequest> exitConditions,
            IndicatorContext indicators,
            ExecutionConfig executionConfig,
            CostModel costModel,
            IntradayDayAccess intradayAccess
    ) {
        if (intradayAccess.looksUsable()) {
            List<IntradayCandlestick> dayBars = intradayAccess.bars();
            int startIdx = firstIndexAtOrAfter(dayBars, state.position.entryTimestamp());
            if (startIdx < dayBars.size()) {
                BacktestEngine.ExitCheckResult intradayExit = engine.checkExitIntraday(
                        state.position,
                        dayBars.subList(startIdx, dayBars.size()),
                        exitRule,
                        state.highWaterMark,
                        executionConfig,
                        costModel
                );
                state.highWaterMark = intradayExit.updatedHighWaterMark();
                return intradayExit.trade();
            }
        }

        return engine.checkExitDaily(state.position, bar, previousBar, exitRule, exitConditions, indicators,
                state.highWaterMark, state.highWaterMark, executionConfig, costModel);
    }

    private int firstIndexAtOrAfter(List<IntradayCandlestick> bars, long timestamp) {
        int idx = 0;
        while (idx < bars.size() && bars.get(idx).getTimestamp() < timestamp) {
            idx++;
        }
        return idx;
    }

    private PendingOrder createPendingOrderFromSignal(Candlestick bar, EntryParams entryParams) {
        if ("NEXT_BAR_OPEN".equals(entryParams.entryMode())) {
            return new PendingOrder(0, 1, entryParams.direction());
        }

        double limitPrice;
        if ("ABOVE".equals(entryParams.direction())) {
            limitPrice = bar.getClose() * (1.0 + entryParams.limitOffsetPercent() / 100.0);
        } else {
            limitPrice = bar.getClose() * (1.0 - entryParams.limitOffsetPercent() / 100.0);
        }
        return new PendingOrder(limitPrice, entryParams.maxBarsToFill(), entryParams.direction());
    }
}
