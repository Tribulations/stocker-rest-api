package com.joakimcolloz.stocker.analysis.filter;

import com.joakimcolloz.stocker.api.entity.Candlestick;
import com.joakimcolloz.stocker.analysis.dto.FilterMatch;
import com.fasterxml.jackson.databind.JsonNode;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Abstract base class for all filter strategies.
 * Filters can operate on either the full time series or a filtered subset.
 */
public abstract class FilterStrategy {

    /**
     * Indicates whether this filter needs the full time series data to calculate.
     * Time-series indicators (RSI, MACD, trends) need full data.
     * Candlestick patterns can work on filtered subsets.
     *
     * @return true if filter needs full time series, false otherwise
     */
    public abstract boolean needsFullTimeSeries();

    /**
     * Applies the filter to the data.
     *
     * @param filteredData The current filtered results (from previous filters)
     * @param fullData The complete time series data
     * @param params Filter-specific parameters from the request
     * @return List of matches that pass this filter
     */
    public abstract List<FilterMatch> apply(
            List<Candlestick> filteredData,
            List<Candlestick> fullData,
            JsonNode params
    );

    /**
     * Converts a list of Candlestick entities to ta4j BarSeries for technical analysis.
     *
     * This helper method is available to all filter implementations that need to use
     * ta4j indicators (RSI, MACD, moving averages, etc.). Each candlestick is converted
     * to a BaseBar with a 1-day duration.
     *
     * @param candlesticks List of candlesticks to convert, ordered chronologically
     * @param seriesName Name for the bar series (typically the stock symbol)
     * @return ta4j BarSeries containing all candlesticks as bars, ready for indicator calculation
     *
     * @see org.ta4j.core.BarSeries
     * @see org.ta4j.core.BaseBar
     */
    protected BarSeries convertToBarSeries(List<Candlestick> candlesticks, String seriesName) {
        BarSeries series = new BaseBarSeriesBuilder().withName(seriesName).build();

         // SORT BY TIMESTAMP ASCENDING - ta4j requires chronological order
        List<Candlestick> sortedCandles = candlesticks.stream()
                .sorted(Comparator.comparingLong(Candlestick::getTimestamp))
                .toList();

        for (Candlestick candle : sortedCandles) {
            Instant instant = Instant.ofEpochSecond(candle.getTimestamp());

            // Create BaseBar
            BaseBar bar = new BaseBar(
                    Duration.ofDays(1),                      // 1. Duration
                    instant,                                  // 2. Begin time (Instant)
                    instant.plus(Duration.ofDays(1)),        // 3. End time (Instant)
                    DecimalNum.valueOf(candle.getOpen()),    // 4. Open price
                    DecimalNum.valueOf(candle.getHigh()),    // 5. High price
                    DecimalNum.valueOf(candle.getLow()),     // 6. Low price
                    DecimalNum.valueOf(candle.getClose()),   // 7. Close price
                    DecimalNum.valueOf(candle.getVolume()),  // 8. Volume
                    DecimalNum.valueOf(0),                   // 9. Amount (0 if unknown)
                    0L                                        // 10. Number of trades (0 if unknown)
            );

            series.addBar(bar);
        }

        return series;
    }
}
