package com.joakimcolloz.stocker.analysis.filter;

import com.joakimcolloz.stocker.api.entity.Candlestick;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterMatch;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.HighVolumeFilterParams;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Filter for identifying candlesticks with volume significantly above average.
 * This is a candlestick filter - works on filtered subset of data.
 */
public class HighVolumeFilter extends FilterStrategy {

    @Override
    public boolean needsFullTimeSeries() {
        return false;  // Can work on filtered subset of data
    }

    @Override
    public List<FilterMatch> apply(
            List<Candlestick> filteredData,
            List<Candlestick> fullData,
            FilterParams params
    ) {
        HighVolumeFilterParams hvParams = (HighVolumeFilterParams) params;
        int percentageAbove = hvParams.getPercentageAbove();

        // Group by symbol
        Map<String, List<Candlestick>> bySymbol = filteredData.stream()
                .collect(Collectors.groupingBy(Candlestick::getSymbol));

        List<FilterMatch> matches = new ArrayList<>();

        for (Map.Entry<String, List<Candlestick>> entry : bySymbol.entrySet()) {
            String symbol = entry.getKey(); // TODO: why declare when unused?
            List<Candlestick> symbolData = entry.getValue();

            // Calculate average volume
            long averageVolume = (long) symbolData.stream()
                    .mapToLong(Candlestick::getVolume)
                    .average()
                    .orElse(0);

            // Find candlesticks exceeding threshold
            long threshold = averageVolume + (averageVolume * percentageAbove / 100);

            for (Candlestick candle : symbolData) {
                if (candle.getVolume() >= threshold) {
                    int actualPercentage = (int) (((double) (candle.getVolume() - averageVolume) / averageVolume) * 100);
                    matches.add(new FilterMatch(
                            candle.getSymbol(),
                            candle.getTimestamp(),
                            candle.getOpen(),
                            candle.getHigh(),
                            candle.getLow(),
                            candle.getClose(),
                            candle.getVolume(),
                            "HIGH_VOLUME",
                            String.format("Volume %d%% above average", actualPercentage)
                    ));
                }
            }
        }

        return matches;
    }
}
