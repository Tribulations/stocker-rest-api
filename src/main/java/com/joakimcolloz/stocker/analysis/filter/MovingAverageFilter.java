package com.joakimcolloz.stocker.analysis.filter;

import com.joakimcolloz.stocker.api.entity.Candlestick;
import com.joakimcolloz.stocker.analysis.indicator.MovingAverageCalculator;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterMatch;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.MovingAverageFilterParams;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Filter for Moving Average crossovers and price-to-MA relationships.
 * This is a time-series filter — needs full data to calculate MA properly.
 * Supports conditions: price_above_ma, price_below_ma, price_crosses_above_ma, price_crosses_below_ma.
 */
public class MovingAverageFilter extends FilterStrategy {

    private static final int DEFAULT_PERIOD = 50;

    @Override
    public boolean needsFullTimeSeries() {
        return true;
    }

    @Override
    public long lookbackSeconds() {
        return MovingAverageCalculator.lookbackSecondsForStableMa(DEFAULT_PERIOD);
    }

    @Override
    public List<FilterMatch> apply(
            List<Candlestick> filteredData,
            List<Candlestick> fullData,
            FilterParams params
    ) {
        if (!(params instanceof MovingAverageFilterParams maParams)) {
            throw new IllegalArgumentException("Invalid parameters for MA_CROSSOVER filter");
        }

        if (maParams.getPeriod() == null || maParams.getMaType() == null || maParams.getCondition() == null) {
            throw new IllegalArgumentException("Missing parameters for MA_CROSSOVER filter");
        }

        int period = maParams.getPeriod();
        String maType = maParams.getMaType();
        String condition = maParams.getCondition();

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

            Map<Long, Double> maByTimestamp = MovingAverageCalculator.calculateByTimestamp(
                    sortedSymbolData, period, maType);
            if (maByTimestamp.isEmpty()) {
                continue;
            }

            // For crossover detection, build sorted timestamp list and index map for close prices
            List<Long> sortedTimestamps = null;
            Map<Long, Double> closeByTimestamp = null;
            if (condition.equals("price_crosses_above_ma") || condition.equals("price_crosses_below_ma")) {
                sortedTimestamps = sortedSymbolData.stream()
                        .map(Candlestick::getTimestamp)
                        .filter(maByTimestamp::containsKey)
                        .sorted()
                        .toList();
                closeByTimestamp = new HashMap<>();
                for (Candlestick c : sortedSymbolData) {
                    closeByTimestamp.put(c.getTimestamp(), c.getClose());
                }
            }

            for (Candlestick candle : symbolFilteredData) {
                Double maValue = maByTimestamp.get(candle.getTimestamp());
                if (maValue == null) {
                    continue;
                }

                boolean matchesCondition = evaluateCondition(
                        condition, candle, maValue, maByTimestamp,
                        sortedTimestamps, closeByTimestamp);

                if (matchesCondition) {
                    matches.add(new FilterMatch(
                            candle.getSymbol(),
                            candle.getTimestamp(),
                            candle.getOpen(),
                            candle.getHigh(),
                            candle.getLow(),
                            candle.getClose(),
                            candle.getVolume(),
                            "MA_CROSSOVER",
                            String.format("%s_%d %s (MA=%.2f, Close=%.2f)",
                                    maType, period, condition, maValue, candle.getClose()),
                            Map.of("ma", maValue),
                            Map.of(
                                    "period", Integer.toString(period),
                                    "maType", maType,
                                    "condition", condition
                            )
                    ));
                }
            }
        }

        return matches;
    }

    private boolean evaluateCondition(
            String condition, Candlestick candle, double maValue,
            Map<Long, Double> maByTimestamp,
            List<Long> sortedTimestamps, Map<Long, Double> closeByTimestamp) {
        return switch (condition) {
            case "price_above_ma" -> candle.getClose() > maValue;
            case "price_below_ma" -> candle.getClose() < maValue;
            case "price_crosses_above_ma" -> {
                int idx = sortedTimestamps.indexOf(candle.getTimestamp());
                if (idx <= 0) yield false;
                long prevTs = sortedTimestamps.get(idx - 1);
                Double prevMa = maByTimestamp.get(prevTs);
                Double prevClose = closeByTimestamp.get(prevTs);
                yield prevMa != null && prevClose != null
                        && prevClose <= prevMa && candle.getClose() > maValue;
            }
            case "price_crosses_below_ma" -> {
                int idx = sortedTimestamps.indexOf(candle.getTimestamp());
                if (idx <= 0) yield false;
                long prevTs = sortedTimestamps.get(idx - 1);
                Double prevMa = maByTimestamp.get(prevTs);
                Double prevClose = closeByTimestamp.get(prevTs);
                yield prevMa != null && prevClose != null
                        && prevClose >= prevMa && candle.getClose() < maValue;
            }
            default -> throw new IllegalArgumentException("Unknown MA condition: " + condition);
        };
    }
}
