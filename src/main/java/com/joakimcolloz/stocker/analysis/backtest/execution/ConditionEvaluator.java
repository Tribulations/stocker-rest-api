package com.joakimcolloz.stocker.analysis.backtest.execution;

import com.joakimcolloz.stocker.analysis.backtest.engine.IndicatorContext;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterRequest;
import com.joakimcolloz.stocker.analysis.dto.filter.HighVolumeFilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.MacdFilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.MovingAverageFilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.RsiFilterParams;
import com.joakimcolloz.stocker.analysis.indicator.MacdCalculator.MacdValues;
import com.joakimcolloz.stocker.api.entity.Candlestick;

import java.util.Map;

public final class ConditionEvaluator {

    private ConditionEvaluator() {
    }

    public static boolean allEntryConditionsMet(
            Candlestick bar,
            Candlestick previousBar,
            java.util.List<FilterRequest> entryConditions,
            IndicatorContext indicators
    ) {
        for (FilterRequest condition : entryConditions) {
            if (!evaluateCondition(bar, previousBar, condition, indicators)) {
                return false;
            }
        }
        return true;
    }

    public static boolean evaluateCondition(
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
            case "PATTERN" -> evaluatePattern(bar, indicators);
            case "MA_CROSSOVER" -> evaluateMovingAverage(bar, previousBar, (MovingAverageFilterParams) params, indicators);
            default -> throw new IllegalArgumentException("Unknown filter type: " + type);
        };
    }

    private static boolean evaluateRsi(Candlestick bar, RsiFilterParams params, IndicatorContext indicators) {
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

    private static boolean evaluateMacd(Candlestick bar, Candlestick previousBar, MacdFilterParams params, IndicatorContext indicators) {
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

    private static boolean evaluateHighVolume(Candlestick bar, HighVolumeFilterParams params, IndicatorContext indicators) {
        if (indicators.avgVolume() <= 0) {
            return false;
        }
        int percentageAbove = params.getPercentageAbove();
        double threshold = indicators.avgVolume() * (1.0 + percentageAbove / 100.0);
        return bar.getVolume() >= threshold;
    }

    private static boolean evaluatePattern(Candlestick bar, IndicatorContext indicators) {
        return indicators.patternTimestamps() != null && indicators.patternTimestamps().contains(bar.getTimestamp());
    }

    private static boolean evaluateMovingAverage(
            Candlestick bar,
            Candlestick previousBar,
            MovingAverageFilterParams params,
            IndicatorContext indicators
    ) {
        String key = params.getMaType() + "_" + params.getPeriod();
        Map<Long, Double> maMap = indicators.maValues() != null ? indicators.maValues().get(key) : null;
        if (maMap == null) return false;

        Double maValue = maMap.get(bar.getTimestamp());
        if (maValue == null) return false;

        return switch (params.getCondition()) {
            case "price_above_ma" -> bar.getClose() > maValue;
            case "price_below_ma" -> bar.getClose() < maValue;
            case "price_crosses_above_ma" -> {
                if (previousBar == null) yield false;
                Double prevMa = maMap.get(previousBar.getTimestamp());
                yield prevMa != null && previousBar.getClose() <= prevMa && bar.getClose() > maValue;
            }
            case "price_crosses_below_ma" -> {
                if (previousBar == null) yield false;
                Double prevMa = maMap.get(previousBar.getTimestamp());
                yield prevMa != null && previousBar.getClose() >= prevMa && bar.getClose() < maValue;
            }
            default -> throw new IllegalArgumentException("Unknown MA condition: " + params.getCondition());
        };
    }
}
