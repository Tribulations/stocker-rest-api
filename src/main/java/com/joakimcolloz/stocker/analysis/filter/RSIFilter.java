package com.joakimcolloz.stocker.analysis.filter;

import com.joakimcolloz.stocker.api.entity.Candlestick;
import com.joakimcolloz.stocker.analysis.indicator.RsiCalculator;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterMatch;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.RsiFilterParams;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Filter for RSI (Relative Strength Index) values.
 * This is a time-series filter - needs full data to calculate RSI properly.
 * To avoid unrealistically low RSI values it is recommended to use at least (DEFAULT_RSI_PERIOD * 2) data points.
 */
public class RSIFilter extends FilterStrategy {

    private static final int DEFAULT_RSI_PERIOD = 14;

    @Override
    public boolean needsFullTimeSeries() {
        return true;  // RSI calculation needs historical context
    }

    @Override
    public long lookbackSeconds() {
        return RsiCalculator.lookbackSecondsForStableRsi(DEFAULT_RSI_PERIOD);
    }

    @Override
    public List<FilterMatch> apply(
            List<Candlestick> filteredData,
            List<Candlestick> fullData,
            FilterParams params // TODO: add RSI period as parameter
    ) {
        // TODO: use contants/enum for conditions and parameters
        if (!(params instanceof RsiFilterParams rsiParams)) {
            throw new IllegalArgumentException("Invalid parameters for RSI filter");
        }

        if (rsiParams.getThreshold() == null || rsiParams.getCondition() == null) {
            throw new IllegalArgumentException("Missing parameters for RSI filter");
        }

        double threshold = rsiParams.getThreshold();
        String condition = rsiParams.getCondition();  // "above" or "below"

        // Group full data by symbol
        // TODO: this logic should be moved to other layer
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

            Map<Long, Double> rsiByTimestamp = RsiCalculator.calculateStableRsiByTimestamp(sortedSymbolData, DEFAULT_RSI_PERIOD);
            if (rsiByTimestamp.isEmpty()) {
                continue;
            }

            // Check filtered data against RSI values
            for (Candlestick candle : symbolFilteredData) {

                // Find index of this candlestick in the series
                Double rsiValueObj = rsiByTimestamp.get(candle.getTimestamp());
                if (rsiValueObj == null) {
                    continue;
                }
                double rsiValue = rsiValueObj;

                boolean matches_condition = condition.equals("above")
                        ? rsiValue > threshold
                        : rsiValue < threshold;

                if (matches_condition) {
                    matches.add(new FilterMatch(
                            candle.getSymbol(),
                            candle.getTimestamp(),
                            candle.getOpen(),
                            candle.getHigh(),
                            candle.getLow(),
                            candle.getClose(),
                            candle.getVolume(),
                            "RSI",
                            String.format("RSI %.2f %s %.2f", rsiValue, condition, threshold),
                            Map.of("rsi", rsiValue),
                            Map.of(
                                    "period", Integer.toString(DEFAULT_RSI_PERIOD),
                                    "threshold", Double.toString(threshold),
                                    "condition", condition
                            )
                    ));
                }
            }
        }

        return matches;
    }

}
