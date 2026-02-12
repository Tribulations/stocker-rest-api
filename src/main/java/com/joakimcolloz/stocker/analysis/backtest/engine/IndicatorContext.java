package com.joakimcolloz.stocker.analysis.backtest.engine;

import com.joakimcolloz.stocker.analysis.indicator.MacdCalculator.MacdValues;

import java.util.Map;
import java.util.Set;

/**
 * Pre-computed indicator data for one symbol, keyed by bar timestamp.
 * Fields are {@code null} when the corresponding indicator is not needed by the backtest.
 *
 * @param rsiValues          timestamp to RSI value (null if RSI not requested)
 * @param macdValues         timestamp to MACD values (null if MACD not requested)
 * @param avgVolume          average volume across the series (0 if volume not requested)
 * @param patternTimestamps  timestamps where the requested pattern was detected (null if PATTERN not requested)
 */
public record IndicatorContext(
        Map<Long, Double> rsiValues,
        Map<Long, MacdValues> macdValues,
        double avgVolume,
        Set<Long> patternTimestamps
) {
}
