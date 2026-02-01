package com.joakimcolloz.stocker.analysis.filter;

import com.joakimcolloz.stocker.api.entity.Candlestick;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterMatch;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.RsiFilterParams;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

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
    public List<FilterMatch> apply(
            List<Candlestick> filteredData,
            List<Candlestick> fullData,
            FilterParams params // TODO: add RSI period as parameter
    ) {
        // TODO: use contants/enum for conditions and parameters
        RsiFilterParams rsiParams = (RsiFilterParams) params;
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

            // Convert to BarSeries
            BarSeries series = convertToBarSeries(sortedSymbolData, symbol);

            if (series.getBarCount() < DEFAULT_RSI_PERIOD + 1) {
                continue;
            }

            // Calculate RSI
            ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
            RSIIndicator rsi = new RSIIndicator(closePrice, DEFAULT_RSI_PERIOD);

            // Check filtered data against RSI values
            for (Candlestick candle : symbolFilteredData) {

                // Find index of this candlestick in the series
                int index = findCandlestickIndex(sortedSymbolData, candle);
                if (index == -1 || index >= series.getBarCount()) continue;

                double rsiValue = rsi.getValue(index).doubleValue();

                if (index < DEFAULT_RSI_PERIOD || Double.isNaN(rsiValue) || rsiValue == 0 || rsiValue == 100) {
                    continue;
                }

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
                            String.format("RSI %.2f %s %.2f", rsiValue, condition, threshold)
                    ));
                }
            }
        }

        return matches;
    }

    /**
     * Finds the index of a candlestick in a list by timestamp.
     */
    private int findCandlestickIndex(List<Candlestick> list, Candlestick target) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getTimestamp() == target.getTimestamp()
                    && list.get(i).getSymbol().equals(target.getSymbol())) {
                return i;
            }
        }
        return -1;
    }
}
