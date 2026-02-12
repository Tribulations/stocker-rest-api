package com.joakimcolloz.stocker.analysis.backtest.engine;

import com.joakimcolloz.stocker.analysis.backtest.dto.ExitRule;
import com.joakimcolloz.stocker.analysis.backtest.dto.Trade;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterRequest;
import com.joakimcolloz.stocker.analysis.dto.filter.HighVolumeFilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.MacdFilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.RsiFilterParams;
import com.joakimcolloz.stocker.analysis.indicator.MacdCalculator.MacdValues;
import com.joakimcolloz.stocker.api.entity.Candlestick;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BacktestEngine {

    private static final long SECONDS_PER_DAY = 86_400L;

    record Position(String symbol, long entryTimestamp, double entryPrice) {
    }

    public List<Trade> runForSymbol(
            String symbol,
            List<Candlestick> sortedCandles,
            long tradingStartTimestamp,
            List<FilterRequest> entryConditions,
            ExitRule exitRule,
            IndicatorContext indicators
    ) {
        List<Trade> trades = new ArrayList<>();
        Position position = null;

        for (int i = 0; i < sortedCandles.size(); i++) {
            Candlestick bar = sortedCandles.get(i);

            if (position != null) {
                // Check exit conditions
                Trade closedTrade = checkExit(position, bar, exitRule);
                if (closedTrade != null) {
                    trades.add(closedTrade);
                    position = null;
                }
            }

            if (position == null && bar.getTimestamp() >= tradingStartTimestamp) {
                // Check entry conditions
                Candlestick previousBar = i > 0 ? sortedCandles.get(i - 1) : null;
                if (allEntryConditionsMet(bar, previousBar, entryConditions, indicators)) {
                    position = new Position(symbol, bar.getTimestamp(), bar.getClose());
                }
            }
        }

        // Close any open position at end of data
        if (position != null && !sortedCandles.isEmpty()) {
            Candlestick lastBar = sortedCandles.getLast();
            trades.add(closeTrade(position, lastBar.getTimestamp(), lastBar.getClose(), "END_OF_DATA"));
        }

        return trades;
    }

    private Trade checkExit(Position position, Candlestick bar, ExitRule exitRule) {
        // Check stop-loss against bar's low
        if (exitRule.stopLossPercent() != null) {
            double stopPrice = position.entryPrice() * (1.0 - exitRule.stopLossPercent() / 100.0);
            if (bar.getLow() <= stopPrice) {
                return closeTrade(position, bar.getTimestamp(), stopPrice, "STOP_LOSS");
            }
        }

        // Check take-profit against bar's high
        if (exitRule.takeProfitPercent() != null) {
            double targetPrice = position.entryPrice() * (1.0 + exitRule.takeProfitPercent() / 100.0);
            if (bar.getHigh() >= targetPrice) {
                return closeTrade(position, bar.getTimestamp(), targetPrice, "TAKE_PROFIT");
            }
        }

        // Check max holding days
        if (exitRule.maxHoldingDays() != null) {
            long holdingSeconds = bar.getTimestamp() - position.entryTimestamp();
            int holdingDays = (int) (holdingSeconds / SECONDS_PER_DAY);
            if (holdingDays >= exitRule.maxHoldingDays()) {
                return closeTrade(position, bar.getTimestamp(), bar.getClose(), "MAX_HOLDING_DAYS");
            }
        }

        return null;
    }

    private Trade closeTrade(Position position, long exitTimestamp, double exitPrice, String exitReason) {
        double returnPercent = (exitPrice - position.entryPrice()) / position.entryPrice() * 100.0;
        int holdingDays = Math.max(1, (int) ((exitTimestamp - position.entryTimestamp()) / SECONDS_PER_DAY));
        return new Trade(
                position.symbol(),
                position.entryTimestamp(),
                position.entryPrice(),
                exitTimestamp,
                exitPrice,
                exitReason,
                returnPercent,
                holdingDays
        );
    }

    private boolean allEntryConditionsMet(
            Candlestick bar,
            Candlestick previousBar,
            List<FilterRequest> entryConditions,
            IndicatorContext indicators
    ) {
        for (FilterRequest condition : entryConditions) {
            if (!evaluateCondition(bar, previousBar, condition, indicators)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateCondition(
            Candlestick bar,
            Candlestick previousBar,
            FilterRequest condition,
            IndicatorContext indicators
    ) {
        String type = condition.getType();
        FilterParams params = condition.getParams();

        return switch (type) {
            case "RSI" -> evaluateRsi(bar, (RsiFilterParams) params, indicators);
            case "MACD" -> evaluateMacd(bar, previousBar, (MacdFilterParams) params, indicators);
            case "HIGH_VOLUME" -> evaluateHighVolume(bar, (HighVolumeFilterParams) params, indicators);
            case "PATTERN" -> throw new IllegalArgumentException(
                    "PATTERN filter is not supported as a backtest entry condition in Phase 1");
            default -> throw new IllegalArgumentException("Unknown filter type: " + type);
        };
    }

    private boolean evaluateRsi(Candlestick bar, RsiFilterParams params, IndicatorContext indicators) {
        if (indicators.rsiValues() == null) {
            return false;
        }
        Double rsiValue = indicators.rsiValues().get(bar.getTimestamp());
        if (rsiValue == null) {
            return false;
        }
        double threshold = params.getThreshold();
        String condition = params.getCondition();
        return "above".equals(condition) ? rsiValue > threshold : rsiValue < threshold;
    }

    private boolean evaluateMacd(Candlestick bar, Candlestick previousBar, MacdFilterParams params, IndicatorContext indicators) {
        if (indicators.macdValues() == null) {
            return false;
        }
        MacdValues current = indicators.macdValues().get(bar.getTimestamp());
        if (current == null) {
            return false;
        }

        String condition = params.getCondition();
        return switch (condition) {
            case "above_zero" -> current.macd() > 0;
            case "below_zero" -> current.macd() < 0;
            case "histogram_positive" -> current.histogram() > 0;
            case "histogram_negative" -> current.histogram() < 0;
            case "crossover_above" -> {
                if (previousBar == null) yield false;
                MacdValues prev = indicators.macdValues().get(previousBar.getTimestamp());
                yield prev != null && current.macd() > current.signal() && prev.macd() <= prev.signal();
            }
            case "crossover_below" -> {
                if (previousBar == null) yield false;
                MacdValues prev = indicators.macdValues().get(previousBar.getTimestamp());
                yield prev != null && current.macd() < current.signal() && prev.macd() >= prev.signal();
            }
            default -> throw new IllegalArgumentException("Unknown MACD condition: " + condition);
        };
    }

    private boolean evaluateHighVolume(Candlestick bar, HighVolumeFilterParams params, IndicatorContext indicators) {
        if (indicators.avgVolume() <= 0) {
            return false;
        }
        int percentageAbove = params.getPercentageAbove();
        double threshold = indicators.avgVolume() * (1.0 + percentageAbove / 100.0);
        return bar.getVolume() >= threshold;
    }
}
