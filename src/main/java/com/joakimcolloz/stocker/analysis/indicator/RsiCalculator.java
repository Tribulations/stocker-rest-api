package com.joakimcolloz.stocker.analysis.indicator;

import com.joakimcolloz.stocker.api.entity.Candlestick;
import com.joakimcolloz.stocker.analysis.ta4j.BarSeriesFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility for computing RSI (Relative Strength Index) values from candlestick data.
 * <p>
 * Delegates to ta4j's {@link RSIIndicator} for the underlying calculation and maps
 * results back to epoch-second timestamps. Provides both "raw" and "stable" variants:
 * <ul>
 *   <li><b>Raw</b> &ndash; requires {@code period + 1} candlesticks minimum.</li>
 *   <li><b>Stable</b> &ndash; requires {@code period * 2} candlesticks so that Wilder's
 *       smoothed average is no longer dominated by its initial seed value.</li>
 * </ul>
 *
 * @see <a href="https://www.investopedia.com/terms/r/rsi.asp">Investopedia &ndash; Relative Strength Index (RSI)</a>
 */
public final class RsiCalculator {
	private static final long SECONDS_PER_DAY = 86400L;

	private RsiCalculator() {
	}

	/**
	 * Returns the number of seconds of extra history needed before the visible window
	 * to produce stable (converged) RSI values.
	 *
	 * @param period RSI period (must be positive)
	 * @return lookback duration in seconds ({@code period * 2 * 86400})
	 */
	public static long lookbackSecondsForStableRsi(int period) {
		if (period <= 0) {
			throw new IllegalArgumentException("RSI period must be positive");
		}
		return (long) period * 2L * SECONDS_PER_DAY;
	}

	/**
	 * Calculates stable RSI values, throwing if the dataset is too small.
	 *
	 * @param candlesticks candlestick data (need not be sorted)
	 * @param period       RSI period
	 * @return map of timestamp to RSI value
	 * @throws IllegalArgumentException if there are not enough candlesticks for a stable calculation
	 */
	public static Map<Long, Double> calculateStableRsiByTimestampOrThrow(List<Candlestick> candlesticks, int period) {
		Map<Long, Double> result = calculateStableRsiByTimestamp(candlesticks, period);
		if (result.isEmpty()) {
			throw new IllegalArgumentException("Not enough candlesticks to calculate stable RSI");
		}
		return result;
	}

	/**
	 * Calculates raw RSI values (minimum {@code period + 1} candlesticks required).
	 *
	 * @param candlesticks candlestick data (need not be sorted)
	 * @param period       RSI period
	 * @return map of timestamp to RSI value, or empty map if data is insufficient
	 */
	public static Map<Long, Double> calculateRsiByTimestamp(List<Candlestick> candlesticks, int period) {
		return calculateRsiByTimestamp(candlesticks, period, period + 1);
	}

	/**
	 * Calculates stable RSI values (minimum {@code period * 2} candlesticks required).
	 *
	 * @param candlesticks candlestick data (need not be sorted)
	 * @param period       RSI period
	 * @return map of timestamp to RSI value, or empty map if data is insufficient
	 */
	public static Map<Long, Double> calculateStableRsiByTimestamp(List<Candlestick> candlesticks, int period) {
		return calculateRsiByTimestamp(candlesticks, period, period * 2);
	}

	private static Map<Long, Double> calculateRsiByTimestamp(List<Candlestick> candlesticks, int period, int minCandlesticks) {
		if (period <= 0) {
			throw new IllegalArgumentException("RSI period must be positive");
		}
		if (candlesticks == null || candlesticks.isEmpty()) {
			return Map.of();
		}

		List<Candlestick> sorted = candlesticks.stream()
				.sorted(Comparator.comparingLong(Candlestick::getTimestamp))
				.toList();

		if (sorted.size() < minCandlesticks) {
			return Map.of();
		}

		BarSeries series = BarSeriesFactory.fromCandlesticks(sorted, "RSI");
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		RSIIndicator rsi = new RSIIndicator(closePrice, period);

		Map<Long, Double> rsiByTimestamp = new HashMap<>();
		for (int i = 0; i < sorted.size(); i++) {
			if (i < period) {
				continue;
			}

			double rsiValue = rsi.getValue(i).doubleValue();
			if (Double.isNaN(rsiValue) || rsiValue == 0.0 || rsiValue == 100.0) {
				continue;
			}

			rsiByTimestamp.put(sorted.get(i).getTimestamp(), rsiValue);
		}

		return Map.copyOf(rsiByTimestamp);
	}
}
