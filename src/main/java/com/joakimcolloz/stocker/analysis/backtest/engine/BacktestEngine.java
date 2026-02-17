package com.joakimcolloz.stocker.analysis.backtest.engine;

import com.joakimcolloz.stocker.analysis.backtest.dto.ExecutionConfig;
import com.joakimcolloz.stocker.analysis.backtest.dto.ExecutionMode;
import com.joakimcolloz.stocker.analysis.backtest.dto.EntryRule;
import com.joakimcolloz.stocker.analysis.backtest.dto.ExitRule;
import com.joakimcolloz.stocker.analysis.backtest.dto.Trade;
import com.joakimcolloz.stocker.analysis.backtest.execution.BpsCostModel;
import com.joakimcolloz.stocker.analysis.backtest.execution.ConditionEvaluator;
import com.joakimcolloz.stocker.analysis.backtest.execution.CostModel;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterRequest;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import com.joakimcolloz.stocker.api.entity.IntradayCandlestick;

import java.util.ArrayList;
import java.util.List;

/**
 * Bar-by-bar backtest engine. No Spring dependencies — pure logic, easy to unit test.
 *
 * <p>Walks candles chronologically for a single symbol using a three-state machine:
 * check exits, try to fill pending orders, then check entry conditions.
 * Supports NEXT_BAR_OPEN and LIMIT_ORDER entry modes.
 * One position at a time per symbol.
 */
public class BacktestEngine {

    private static final long SECONDS_PER_DAY = 86_400L;
    private static final long INTRADAY_BAR_SECONDS = 300L;

    @FunctionalInterface
    public interface IntradayCandlesProvider {
        List<IntradayCandlestick> fetch(long startTimestampInclusive, long endTimestampExclusive);
    }

    private record ExitCheckResult(Trade trade, double updatedHighWaterMark) {
    }

    record Position(String symbol, long entryTimestamp, double entryPrice) {
    }

    private static class PendingOrder {
        final double limitPrice;
        final String direction; // "ABOVE" or "BELOW"
        int barsRemaining;

        PendingOrder(double limitPrice, int barsRemaining, String direction) {
            this.limitPrice = limitPrice;
            this.barsRemaining = barsRemaining;
            this.direction = direction;
        }
    }

    /**
     * Runs the backtest for a single symbol over the given candle series.
     *
     * @param symbol                ticker symbol
     * @param sortedCandles         candles sorted by timestamp ascending (may include lookback period)
     * @param tradingStartTimestamp earliest timestamp at which entries are allowed
     * @param entryConditions       filter conditions that must all be satisfied to enter
     * @param exitRule              stop-loss / take-profit / max holding days
     * @param exitConditions        indicator-based exit conditions (OR logic), or null for none
     * @param indicators            pre-computed indicator values for this symbol
     * @param entryRule             controls entry mode (NEXT_BAR_OPEN or LIMIT_ORDER)
     * @return list of completed trades
     */
    public List<Trade> runForSymbol(
            String symbol,
            List<Candlestick> sortedCandles,
            long tradingStartTimestamp,
            List<FilterRequest> entryConditions,
            ExitRule exitRule,
            List<FilterRequest> exitConditions,
            IndicatorContext indicators,
            EntryRule entryRule
    ) {
        return runForSymbol(symbol, sortedCandles, tradingStartTimestamp, entryConditions, exitRule,
                exitConditions, indicators, entryRule,
                new ExecutionConfig(null, null, null, null, null, null), List.of(), new BpsCostModel(0.0, 0.0, 0.0));
    }

    public List<Trade> runForSymbol(
            String symbol,
            List<Candlestick> sortedCandles,
            long tradingStartTimestamp,
            List<FilterRequest> entryConditions,
            ExitRule exitRule,
            List<FilterRequest> exitConditions,
            IndicatorContext indicators,
            EntryRule entryRule,
            ExecutionConfig executionConfig,
            List<IntradayCandlestick> sortedIntradayCandles
    ) {
        return runForSymbol(symbol, sortedCandles, tradingStartTimestamp, entryConditions, exitRule, exitConditions,
                indicators, entryRule, executionConfig, sortedIntradayCandles,
                new BpsCostModel(executionConfig.slippageBps(), executionConfig.spreadBps(), executionConfig.commissionBps()));
    }

    public List<Trade> runForSymbol(
            String symbol,
            List<Candlestick> sortedCandles,
            long tradingStartTimestamp,
            List<FilterRequest> entryConditions,
            ExitRule exitRule,
            List<FilterRequest> exitConditions,
            IndicatorContext indicators,
            EntryRule entryRule,
            ExecutionConfig executionConfig,
            List<IntradayCandlestick> sortedIntradayCandles,
            CostModel costModel
    ) {
        List<Trade> trades = new ArrayList<>();
        Position position = null;
        PendingOrder pendingOrder = null;
        double highWaterMark = 0.0;

        int intradayIndex = 0;

        String entryMode = entryRule.entryMode();
        double limitOffsetPercent = entryRule.limitOffsetPercent() != null ? entryRule.limitOffsetPercent() : 0.0;
        double penetrationPercent = entryRule.penetrationPercent() != null ? entryRule.penetrationPercent() : 0.0;
        int maxBarsToFill = entryRule.maxBarsToFill() != null ? entryRule.maxBarsToFill() : 1;
        String direction = entryRule.limitDirection() != null ? entryRule.limitDirection() : "BELOW";

        for (int i = 0; i < sortedCandles.size(); i++) {
            Candlestick bar = sortedCandles.get(i);
            Candlestick previousBar = i > 0 ? sortedCandles.get(i - 1) : null;

            // 1. Check exits (existing logic)
            if (position != null) {
                Trade closedTrade;

                if (executionConfig.mode() == ExecutionMode.HYBRID_INTRADAY && !sortedIntradayCandles.isEmpty()) {
                    int startIdx = intradayIndex;
                    while (startIdx < sortedIntradayCandles.size()
                            && sortedIntradayCandles.get(startIdx).getTimestamp() < bar.getTimestamp()) {
                        startIdx++;
                    }
                    int endIdx = startIdx;
                    while (endIdx < sortedIntradayCandles.size()
                            && sortedIntradayCandles.get(endIdx).getTimestamp() < (bar.getTimestamp() + SECONDS_PER_DAY)) {
                        endIdx++;
                    }
                    intradayIndex = startIdx;

                    boolean intradaySliceLooksUsable = startIdx < endIdx
                            && sortedIntradayCandles.get(startIdx).getTimestamp() <= (bar.getTimestamp() + INTRADAY_BAR_SECONDS);

                    if (intradaySliceLooksUsable) {
                        ExitCheckResult intradayExit = checkExitIntraday(
                                position,
                                sortedIntradayCandles.subList(startIdx, endIdx),
                                exitRule,
                                highWaterMark,
                                executionConfig,
                                costModel
                        );
                        closedTrade = intradayExit.trade();
                        highWaterMark = intradayExit.updatedHighWaterMark();
                        intradayIndex = endIdx;
                    } else {
                        closedTrade = checkExitDaily(position, bar, previousBar, exitRule, exitConditions, indicators,
                                highWaterMark, highWaterMark, executionConfig, costModel);
                    }
                } else {
                    closedTrade = checkExitDaily(position, bar, previousBar, exitRule, exitConditions, indicators,
                            highWaterMark, highWaterMark, executionConfig, costModel);
                }

                if (closedTrade != null) {
                    trades.add(closedTrade);
                    position = null;
                    highWaterMark = 0.0;
                } else {
                    if (Boolean.TRUE.equals(exitRule.trailingStopLoss())) {
                        if (executionConfig.mode() == ExecutionMode.HYBRID_INTRADAY && !sortedIntradayCandles.isEmpty()) {
                            // In intraday mode, trailing HWM is updated via intraday bar closes,
                            // and we conservatively also update at the daily close.
                            highWaterMark = Math.max(highWaterMark, bar.getClose());
                        } else {
                            highWaterMark = Math.max(highWaterMark, bar.getClose());
                        }
                    }
                }
            }

            // 2. Try to fill pending order
            if (pendingOrder != null && position == null) {
                if ("NEXT_BAR_OPEN".equals(entryMode)) {
                    double entryFill = costModel.applyEntryPrice(bar.getOpen());
                    position = new Position(symbol, bar.getTimestamp(), entryFill);
                    pendingOrder = null;
                } else { // LIMIT_ORDER
                    if ("ABOVE".equals(pendingOrder.direction)) {
                        double fillThreshold = pendingOrder.limitPrice * (1.0 + penetrationPercent / 100.0);
                        if (bar.getHigh() >= fillThreshold) {
                            double entryFill = costModel.applyEntryPrice(pendingOrder.limitPrice);
                            position = new Position(symbol, bar.getTimestamp(), entryFill);
                            pendingOrder = null;
                        } else {
                            pendingOrder.barsRemaining--;
                            if (pendingOrder.barsRemaining <= 0) pendingOrder = null;
                        }
                    } else {
                        double fillThreshold = pendingOrder.limitPrice * (1.0 - penetrationPercent / 100.0);
                        if (bar.getLow() <= fillThreshold) {
                            double entryFill = costModel.applyEntryPrice(pendingOrder.limitPrice);
                            position = new Position(symbol, bar.getTimestamp(), entryFill);
                            pendingOrder = null;
                        } else {
                            pendingOrder.barsRemaining--;
                            if (pendingOrder.barsRemaining <= 0) pendingOrder = null;
                        }
                    }
                }

                if (position != null) {
                    highWaterMark = position.entryPrice();
                }

                // Check exits on the same bar where we just entered (intrabar stop/TP)
                if (position != null) {
                    Trade closedTrade;
                    if (executionConfig.mode() == ExecutionMode.HYBRID_INTRADAY && !sortedIntradayCandles.isEmpty()) {
                        int startIdx = intradayIndex;
                        while (startIdx < sortedIntradayCandles.size()
                                && sortedIntradayCandles.get(startIdx).getTimestamp() < bar.getTimestamp()) {
                            startIdx++;
                        }
                        int endIdx = startIdx;
                        while (endIdx < sortedIntradayCandles.size()
                                && sortedIntradayCandles.get(endIdx).getTimestamp() < (bar.getTimestamp() + SECONDS_PER_DAY)) {
                            endIdx++;
                        }
                        intradayIndex = startIdx;
                        boolean intradaySliceLooksUsable = startIdx < endIdx
                                && sortedIntradayCandles.get(startIdx).getTimestamp() <= (bar.getTimestamp() + INTRADAY_BAR_SECONDS);

                        if (intradaySliceLooksUsable) {
                            ExitCheckResult intradayExit = checkExitIntraday(
                                    position,
                                    sortedIntradayCandles.subList(startIdx, endIdx),
                                    exitRule,
                                    highWaterMark,
                                    executionConfig,
                                    costModel
                            );
                            closedTrade = intradayExit.trade();
                            highWaterMark = intradayExit.updatedHighWaterMark();
                            intradayIndex = endIdx;
                        } else {
                            closedTrade = checkExitDaily(position, bar, previousBar, exitRule, exitConditions, indicators,
                                    highWaterMark, highWaterMark, executionConfig, costModel);
                        }
                    } else {
                        closedTrade = checkExitDaily(position, bar, previousBar, exitRule, exitConditions, indicators,
                                highWaterMark, highWaterMark, executionConfig, costModel);
                    }

                    if (closedTrade != null) {
                        trades.add(closedTrade);
                        position = null;
                        highWaterMark = 0.0;
                    } else {
                        if (Boolean.TRUE.equals(exitRule.trailingStopLoss())) {
                            if (executionConfig.mode() == ExecutionMode.HYBRID_INTRADAY && !sortedIntradayCandles.isEmpty()) {
                                highWaterMark = Math.max(highWaterMark, bar.getClose());
                            } else {
                                highWaterMark = Math.max(highWaterMark, bar.getClose());
                            }
                        }
                    }
                }
            }

            // 3. Check entry conditions (only if no position AND no pending order)
            if (position == null && pendingOrder == null && bar.getTimestamp() >= tradingStartTimestamp) {
                if (ConditionEvaluator.allEntryConditionsMet(bar, previousBar, entryConditions, indicators)) {
                    if ("NEXT_BAR_OPEN".equals(entryMode)) {
                        pendingOrder = new PendingOrder(0, 1, direction);
                    } else { // LIMIT_ORDER
                        double limitPrice;
                        if ("ABOVE".equals(direction)) {
                            limitPrice = bar.getClose() * (1.0 + limitOffsetPercent / 100.0);
                        } else {
                            limitPrice = bar.getClose() * (1.0 - limitOffsetPercent / 100.0);
                        }
                        pendingOrder = new PendingOrder(limitPrice, maxBarsToFill, direction);
                    }
                }
            }
        }

        // Close any open position at end of data
        if (position != null && !sortedCandles.isEmpty()) {
            Candlestick lastBar = sortedCandles.getLast();
            trades.add(closeTrade(position, lastBar.getTimestamp(), lastBar.getClose(), "END_OF_DATA", costModel));
        }
        // Pending orders at end of data are simply dropped (no forced fill)

        return trades;
    }

    public List<Trade> runForSymbol(
            String symbol,
            List<Candlestick> sortedCandles,
            long tradingStartTimestamp,
            List<FilterRequest> entryConditions,
            ExitRule exitRule,
            List<FilterRequest> exitConditions,
            IndicatorContext indicators,
            EntryRule entryRule,
            ExecutionConfig executionConfig,
            IntradayCandlesProvider intradayCandlesProvider,
            CostModel costModel
    ) {
        List<Trade> trades = new ArrayList<>();
        RunState state = new RunState();

        EntryParams entryParams = entryParams(entryRule);
        boolean useIntraday = executionConfig.mode() == ExecutionMode.HYBRID_INTRADAY && intradayCandlesProvider != null;

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
                Position entered = tryFillPendingOrder(symbol, bar, entryParams, state.pendingOrder, costModel, intradayAccess);
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
            trades.add(closeTrade(state.position, lastBar.getTimestamp(), lastBar.getClose(), "END_OF_DATA", costModel));
        }

        return trades;
    }

    private static final class RunState {
        Position position;
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

    private static final class IntradayDayAccess {
        private final boolean useIntraday;
        private final IntradayCandlesProvider provider;
        private final long dayStart;
        private List<IntradayCandlestick> cached;

        private IntradayDayAccess(boolean useIntraday, IntradayCandlesProvider provider, long dayStart) {
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
            ExitCheckResult intradayExit = checkExitIntraday(
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

        return checkExitDaily(state.position, bar, previousBar, exitRule, exitConditions, indicators,
                state.highWaterMark, state.highWaterMark, executionConfig, costModel);
    }

    private void updateTrailingHighWaterMarkIfNeeded(RunState state, Candlestick bar, ExitRule exitRule) {
        if (Boolean.TRUE.equals(exitRule.trailingStopLoss())) {
            state.highWaterMark = Math.max(state.highWaterMark, bar.getClose());
        }
    }

    private Position tryFillPendingOrder(
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
            return new Position(symbol, entryTs, entryFill);
        }

        Long entryTs = null;
        Double rawEntryPrice = null;
        if (intradayAccess.looksUsable()) {
            IntradayEntryFill fill = tryFillLimitIntraday(pendingOrder, entryParams.penetrationPercent(), intradayAccess.bars());
            if (fill != null) {
                entryTs = fill.entryTimestamp;
                rawEntryPrice = fill.rawEntryPrice;
            }
        }

        if (entryTs == null) {
            DailyEntryFill fill = tryFillLimitDaily(pendingOrder, entryParams.penetrationPercent(), dailyBar);
            if (fill != null) {
                entryTs = fill.entryTimestamp;
                rawEntryPrice = fill.rawEntryPrice;
            }
        }

        if (entryTs == null || rawEntryPrice == null) {
            return null;
        }

        double entryFill = costModel.applyEntryPrice(rawEntryPrice);
        return new Position(symbol, entryTs, entryFill);
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
                ExitCheckResult intradayExit = checkExitIntraday(
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

        return checkExitDaily(state.position, bar, previousBar, exitRule, exitConditions, indicators,
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

    private Trade checkExitDaily(Position position, Candlestick bar, Candlestick previousBar,
                                ExitRule exitRule, List<FilterRequest> exitConditions,
                                IndicatorContext indicators, double preBarHighWaterMark, double highWaterMark,
                                ExecutionConfig executionConfig, CostModel costModel) {
        Trade stopExit = null;
        if (exitRule.stopLossPercent() != null) {
            boolean trailing = Boolean.TRUE.equals(exitRule.trailingStopLoss());

            double basePriceAtOpen = trailing ? preBarHighWaterMark : position.entryPrice();
            double stopPriceAtOpen = basePriceAtOpen * (1.0 - exitRule.stopLossPercent() / 100.0);

            double basePrice = trailing ? highWaterMark : position.entryPrice();
            double stopPrice = basePrice * (1.0 - exitRule.stopLossPercent() / 100.0);

            if (bar.getLow() <= stopPrice) {
                String reason = trailing ? "TRAILING_STOP_LOSS" : "STOP_LOSS";
                double fillPrice = bar.getOpen() <= stopPriceAtOpen ? bar.getOpen() : stopPrice;
                stopExit = closeTrade(position, bar.getTimestamp(), fillPrice, reason, costModel);
            }
        }

        Trade takeProfitExit = null;
        if (exitRule.takeProfitPercent() != null) {
            double targetPrice = position.entryPrice() * (1.0 + exitRule.takeProfitPercent() / 100.0);
            if (bar.getHigh() >= targetPrice) {
                double fillPrice = bar.getOpen() >= targetPrice ? bar.getOpen() : targetPrice;
                takeProfitExit = closeTrade(position, bar.getTimestamp(), fillPrice, "TAKE_PROFIT", costModel);
            }
        }

        if (stopExit != null || takeProfitExit != null) {
            return stopExit != null ? stopExit : takeProfitExit;
        }

        // 3. Indicator-based exit conditions (OR logic — any condition triggers exit)
        if (exitConditions != null) {
            for (FilterRequest condition : exitConditions) {
                if (ConditionEvaluator.evaluateCondition(bar, previousBar, condition, indicators)) {
                    return closeTrade(position, bar.getTimestamp(), bar.getClose(),
                            condition.getType() + "_EXIT", costModel);
                }
            }
        }

        // 4. Max holding days (lowest priority)
        if (exitRule.maxHoldingDays() != null) {
            long holdingSeconds = bar.getTimestamp() - position.entryTimestamp();
            int holdingDays = (int) (holdingSeconds / SECONDS_PER_DAY);
            if (holdingDays >= exitRule.maxHoldingDays()) {
                return closeTrade(position, bar.getTimestamp(), bar.getClose(), "MAX_HOLDING_DAYS", costModel);
            }
        }

        return null;
    }

    private ExitCheckResult checkExitIntraday(
            Position position,
            List<IntradayCandlestick> bars,
            ExitRule exitRule,
            double startingHighWaterMark,
            ExecutionConfig executionConfig,
            CostModel costModel
    ) {
        double highWaterMark = startingHighWaterMark;

        for (IntradayCandlestick bar : bars) {
            boolean trailing = Boolean.TRUE.equals(exitRule.trailingStopLoss());
            double preBarHwm = highWaterMark;

            double stopFillRaw = 0.0;
            boolean stopHit = false;
            String stopReason = null;

            if (exitRule.stopLossPercent() != null) {
                double basePriceAtOpen = trailing ? preBarHwm : position.entryPrice();
                double stopPriceAtOpen = basePriceAtOpen * (1.0 - exitRule.stopLossPercent() / 100.0);
                double basePrice = trailing ? preBarHwm : position.entryPrice();
                double stopPrice = basePrice * (1.0 - exitRule.stopLossPercent() / 100.0);

                if (bar.getLow() <= stopPrice) {
                    stopHit = true;
                    stopReason = trailing ? "TRAILING_STOP_LOSS" : "STOP_LOSS";
                    if (bar.getOpen() <= stopPriceAtOpen) {
                        stopFillRaw = bar.getOpen();
                    } else {
                        stopFillRaw = stopPrice;
                    }
                }
            }

            double takeProfitFillRaw = 0.0;
            boolean takeProfitHit = false;
            if (exitRule.takeProfitPercent() != null) {
                double targetPrice = position.entryPrice() * (1.0 + exitRule.takeProfitPercent() / 100.0);
                if (bar.getHigh() >= targetPrice) {
                    takeProfitHit = true;
                    takeProfitFillRaw = bar.getOpen() >= targetPrice ? bar.getOpen() : targetPrice;
                }
            }

            if (stopHit || takeProfitHit) {
                boolean chooseStop = stopHit && (executionConfig.conservativeFillMode() || !takeProfitHit);
                if (!stopHit && takeProfitHit) {
                    chooseStop = false;
                }
                if (stopHit && takeProfitHit && !executionConfig.conservativeFillMode()) {
                    chooseStop = false;
                }

                if (chooseStop) {
                    return new ExitCheckResult(
                            closeTrade(position, bar.getTimestamp(), stopFillRaw, stopReason, costModel),
                            highWaterMark
                    );
                }
                return new ExitCheckResult(
                        closeTrade(position, bar.getTimestamp(), takeProfitFillRaw, "TAKE_PROFIT", costModel),
                        highWaterMark
                );
            }

            if (trailing) {
                highWaterMark = Math.max(highWaterMark, bar.getClose());
            }
        }

        return new ExitCheckResult(null, highWaterMark);
    }

    private Trade closeTrade(Position position, long exitTimestamp, double exitPrice, String exitReason,
                             CostModel costModel) {
        double adjustedExitPrice = costModel.applyExitPrice(exitPrice);
        double returnPercent = costModel.computeReturnPercent(position.entryPrice(), adjustedExitPrice);

        int holdingDays = Math.max(1, (int) ((exitTimestamp - position.entryTimestamp()) / SECONDS_PER_DAY));
        return new Trade(
                position.symbol(),
                position.entryTimestamp(),
                position.entryPrice(),
                exitTimestamp,
                adjustedExitPrice,
                exitReason,
                returnPercent,
                holdingDays
        );
    }

}
