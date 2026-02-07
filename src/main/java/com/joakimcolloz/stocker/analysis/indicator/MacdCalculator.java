package com.joakimcolloz.stocker.analysis.indicator;

import com.joakimcolloz.stocker.api.entity.Candlestick;
import com.joakimcolloz.stocker.analysis.ta4j.BarSeriesFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility for computing MACD (Moving Average Convergence Divergence) values from candlestick data.
 * <p>
 * Delegates to ta4j's {@link MACDIndicator} for the underlying calculation and maps
 * results back to epoch-second timestamps. Provides both "raw" and "stable" variants:
 * <ul>
 *   <li><b>Raw</b> &ndash; requires {@code slowPeriod + signalPeriod} candlesticks minimum.</li>
 *   <li><b>Stable</b> &ndash; requires {@code slowPeriod * 2} candlesticks so that the
 *       EMA averages are no longer dominated by their initial seed values.</li>
 * </ul>
 *
 * @see <a href="https://www.investopedia.com/terms/m/macd.asp">Investopedia &ndash; MACD</a>
 */
public final class MacdCalculator {
	private static final long SECONDS_PER_DAY = 86400L;

	public record MacdValues(double macd, double signal, double histogram) {
	}

	private MacdCalculator() {
	}

	/**
	 * Returns the number of seconds of extra history needed before the visible window
	 * to produce stable (converged) MACD values.
	 *
	 * @param slowPeriod MACD slow period (must be positive)
	 * @return lookback duration in seconds ({@code slowPeriod * 2 * 86400})
	 */
	public static long lookbackSecondsForStableMacd(int slowPeriod) {
		if (slowPeriod <= 0) {
			throw new IllegalArgumentException("MACD slow period must be positive");
		}
		return (long) slowPeriod * 2L * SECONDS_PER_DAY;
	}

	/**
	 * Calculates stable MACD values, throwing if the dataset is too small.
	 *
	 * @param candlesticks candlestick data (need not be sorted)
	 * @param fastPeriod   MACD fast period
	 * @param slowPeriod   MACD slow period
	 * @param signalPeriod MACD signal period
	 * @return map of timestamp to MACD values
	 * @throws IllegalArgumentException if there are not enough candlesticks for a stable calculation
	 */
	public static Map<Long, MacdValues> calculateStableMacdByTimestampOrThrow(
			List<Candlestick> candlesticks, int fastPeriod, int slowPeriod, int signalPeriod) {
		Map<Long, MacdValues> result = calculateStableMacdByTimestamp(candlesticks, fastPeriod, slowPeriod, signalPeriod);
		if (result.isEmpty()) {
			throw new IllegalArgumentException("Not enough candlesticks to calculate stable MACD");
		}
		return result;
	}

	/**
	 * Calculates raw MACD values (minimum {@code slowPeriod + signalPeriod} candlesticks required).
	 *
	 * @param candlesticks candlestick data (need not be sorted)
	 * @param fastPeriod   MACD fast period
	 * @param slowPeriod   MACD slow period
	 * @param signalPeriod MACD signal period
	 * @return map of timestamp to MACD values, or empty map if data is insufficient
	 */
	public static Map<Long, MacdValues> calculateMacdByTimestamp(
			List<Candlestick> candlesticks, int fastPeriod, int slowPeriod, int signalPeriod) {
		return calculateMacdByTimestamp(candlesticks, fastPeriod, slowPeriod, signalPeriod, slowPeriod + signalPeriod);
	}

	/**
	 * Calculates stable MACD values (minimum {@code slowPeriod * 2} candlesticks required).
	 *
	 * @param candlesticks candlestick data (need not be sorted)
	 * @param fastPeriod   MACD fast period
	 * @param slowPeriod   MACD slow period
	 * @param signalPeriod MACD signal period
	 * @return map of timestamp to MACD values, or empty map if data is insufficient
	 */
	public static Map<Long, MacdValues> calculateStableMacdByTimestamp(
			List<Candlestick> candlesticks, int fastPeriod, int slowPeriod, int signalPeriod) {
		return calculateMacdByTimestamp(candlesticks, fastPeriod, slowPeriod, signalPeriod, slowPeriod * 2);
	}

	private static Map<Long, MacdValues> calculateMacdByTimestamp(
			List<Candlestick> candlesticks, int fastPeriod, int slowPeriod, int signalPeriod, int minCandlesticks) {
		if (fastPeriod <= 0 || slowPeriod <= 0 || signalPeriod <= 0) {
			throw new IllegalArgumentException("MACD periods must be positive");
		}
		if (fastPeriod >= slowPeriod) {
			throw new IllegalArgumentException("Fast period must be less than slow period");
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

		BarSeries series = BarSeriesFactory.fromCandlesticks(sorted, "MACD");
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		MACDIndicator macdIndicator = new MACDIndicator(closePrice, fastPeriod, slowPeriod);
		EMAIndicator signalIndicator = new EMAIndicator(macdIndicator, signalPeriod);

		int startIndex = slowPeriod + signalPeriod - 2;

		Map<Long, MacdValues> macdByTimestamp = new HashMap<>();
		for (int i = startIndex; i < sorted.size(); i++) {
			double macdValue = macdIndicator.getValue(i).doubleValue();
			double signalValue = signalIndicator.getValue(i).doubleValue();
			double histogram = macdValue - signalValue;

			if (Double.isNaN(macdValue) || Double.isNaN(signalValue)) {
				continue;
			}

			macdByTimestamp.put(sorted.get(i).getTimestamp(),
					new MacdValues(macdValue, signalValue, histogram));
		}

		return Map.copyOf(macdByTimestamp);
	}
}
