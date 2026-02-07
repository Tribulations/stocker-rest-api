package com.joakimcolloz.stocker.analysis.filter;

import com.joakimcolloz.stocker.api.entity.Candlestick;
import com.joakimcolloz.stocker.analysis.indicator.MacdCalculator;
import com.joakimcolloz.stocker.analysis.indicator.MacdCalculator.MacdValues;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterMatch;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.MacdFilterParams;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Filter for MACD (Moving Average Convergence Divergence) values.
 * This is a time-series filter - needs full data to calculate MACD properly.
 * Supports six conditions: crossover_above, crossover_below, above_zero, below_zero,
 * histogram_positive, histogram_negative.
 */
public class MacdFilter extends FilterStrategy {

    private static final int DEFAULT_FAST_PERIOD = 12;
    private static final int DEFAULT_SLOW_PERIOD = 26;
    private static final int DEFAULT_SIGNAL_PERIOD = 9;

    @Override
    public boolean needsFullTimeSeries() {
        return true;
    }

    @Override
    public long lookbackSeconds() {
        return MacdCalculator.lookbackSecondsForStableMacd(DEFAULT_SLOW_PERIOD);
    }

    @Override
    public List<FilterMatch> apply(
            List<Candlestick> filteredData,
            List<Candlestick> fullData,
            FilterParams params
    ) {
        if (!(params instanceof MacdFilterParams macdParams)) {
            throw new IllegalArgumentException("Invalid parameters for MACD filter");
        }

        if (macdParams.getCondition() == null) {
            throw new IllegalArgumentException("Missing condition for MACD filter");
        }

        int fastPeriod = macdParams.getFastPeriod() != null ? macdParams.getFastPeriod() : DEFAULT_FAST_PERIOD;
        int slowPeriod = macdParams.getSlowPeriod() != null ? macdParams.getSlowPeriod() : DEFAULT_SLOW_PERIOD;
        int signalPeriod = macdParams.getSignalPeriod() != null ? macdParams.getSignalPeriod() : DEFAULT_SIGNAL_PERIOD;
        String condition = macdParams.getCondition();

        // Group full data by symbol
        Map<String, List<Candlestick>> bySymbol = fullData.stream()
                .collect(Collectors.groupingBy(Candlestick::getSymbol));

        // Only evaluate symbols that are still present after previous filters
        Map<String, List<Candlestick>> filteredBySymbol = filteredData.stream()
                .collect(Collectors.groupingBy(Candlestick::getSymbol));

        List<FilterMatch> matches = new ArrayList<>();

        for (Map.Entry<String, List<Candlestick>> entry : filteredBySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<Candlestick> symbolFilteredData = entry.getValue();

            List<Candlestick> symbolData = bySymbol.get(symbol);
            if (symbolData == null || symbolData.isEmpty()) {
                continue;
            }

            List<Candlestick> sortedSymbolData = symbolData.stream()
                    .sorted(Comparator.comparingLong(Candlestick::getTimestamp))
                    .toList();

            Map<Long, MacdValues> macdByTimestamp = MacdCalculator.calculateStableMacdByTimestamp(
                    sortedSymbolData, fastPeriod, slowPeriod, signalPeriod);
            if (macdByTimestamp.isEmpty()) {
                continue;
            }

            // For crossover detection, build sorted timestamp list and index map
            List<Long> sortedTimestamps = null;
            Map<Long, Integer> timestampIndex = null;
            if (condition.equals("crossover_above") || condition.equals("crossover_below")) {
                sortedTimestamps = sortedSymbolData.stream()
                        .map(Candlestick::getTimestamp)
                        .filter(macdByTimestamp::containsKey)
                        .sorted()
                        .toList();
                timestampIndex = new HashMap<>();
                for (int i = 0; i < sortedTimestamps.size(); i++) {
                    timestampIndex.put(sortedTimestamps.get(i), i);
                }
            }

            for (Candlestick candle : symbolFilteredData) {
                MacdValues values = macdByTimestamp.get(candle.getTimestamp());
                if (values == null) {
                    continue;
                }

                boolean matchesCondition = evaluateCondition(
                        condition, values, candle.getTimestamp(),
                        macdByTimestamp, sortedTimestamps, timestampIndex);

                if (matchesCondition) {
                    matches.add(new FilterMatch(
                            candle.getSymbol(),
                            candle.getTimestamp(),
                            candle.getOpen(),
                            candle.getHigh(),
                            candle.getLow(),
                            candle.getClose(),
                            candle.getVolume(),
                            "MACD",
                            String.format("MACD %s (%.4f / %.4f / %.4f)",
                                    condition, values.macd(), values.signal(), values.histogram()),
                            Map.of(
                                    "macd", values.macd(),
                                    "signal", values.signal(),
                                    "histogram", values.histogram()
                            ),
                            Map.of(
                                    "fastPeriod", Integer.toString(fastPeriod),
                                    "slowPeriod", Integer.toString(slowPeriod),
                                    "signalPeriod", Integer.toString(signalPeriod),
                                    "condition", condition
                            )
                    ));
                }
            }
        }

        return matches;
    }

    private boolean evaluateCondition(
            String condition, MacdValues current, long timestamp,
            Map<Long, MacdValues> macdByTimestamp,
            List<Long> sortedTimestamps, Map<Long, Integer> timestampIndex) {
        return switch (condition) {
            case "above_zero" -> current.macd() > 0;
            case "below_zero" -> current.macd() < 0;
            case "histogram_positive" -> current.histogram() > 0;
            case "histogram_negative" -> current.histogram() < 0;
            case "crossover_above" -> {
                Integer idx = timestampIndex.get(timestamp);
                if (idx == null || idx == 0) yield false;
                long prevTs = sortedTimestamps.get(idx - 1);
                MacdValues prev = macdByTimestamp.get(prevTs);
                yield prev != null && current.macd() > current.signal() && prev.macd() <= prev.signal();
            }
            case "crossover_below" -> {
                Integer idx = timestampIndex.get(timestamp);
                if (idx == null || idx == 0) yield false;
                long prevTs = sortedTimestamps.get(idx - 1);
                MacdValues prev = macdByTimestamp.get(prevTs);
                yield prev != null && current.macd() < current.signal() && prev.macd() >= prev.signal();
            }
            default -> throw new IllegalArgumentException("Unknown MACD condition: " + condition);
        };
    }
}
