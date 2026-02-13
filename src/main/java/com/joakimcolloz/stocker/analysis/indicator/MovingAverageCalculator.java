package com.joakimcolloz.stocker.analysis.indicator;

import com.joakimcolloz.stocker.api.entity.Candlestick;
import com.joakimcolloz.stocker.analysis.ta4j.BarSeriesFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility for computing Moving Average (SMA / EMA) values from candlestick data.
 * <p>
 * Delegates to ta4j's {@link SMAIndicator} and {@link EMAIndicator} for the underlying
 * calculation and maps results back to epoch-second timestamps.
 * Skips the first {@code period} bars where the MA is not yet stable.
 */
public final class MovingAverageCalculator {
	private static final long SECONDS_PER_DAY = 86400L;

	private MovingAverageCalculator() {
	}

	/**
	 * Returns the number of seconds of extra history needed before the visible window
	 * to produce stable moving average values.
	 *
	 * @param period MA period (must be positive)
	 * @return lookback duration in seconds ({@code period * 2 * 86400})
	 */
	public static long lookbackSecondsForStableMa(int period) {
		if (period <= 0) {
			throw new IllegalArgumentException("MA period must be positive");
		}
		return (long) period * 2L * SECONDS_PER_DAY;
	}

	/**
	 * Calculates moving average values keyed by timestamp.
	 *
	 * @param candlesticks candlestick data (need not be sorted)
	 * @param period       MA period (e.g. 20, 50, 200)
	 * @param maType       "SMA" or "EMA"
	 * @return map of timestamp to MA value, or empty map if data is insufficient
	 */
	public static Map<Long, Double> calculateByTimestamp(List<Candlestick> candlesticks, int period, String maType) {
		if (period <= 0) {
			throw new IllegalArgumentException("MA period must be positive");
		}
		if (maType == null || (!"SMA".equals(maType) && !"EMA".equals(maType))) {
			throw new IllegalArgumentException("maType must be \"SMA\" or \"EMA\"");
		}
		if (candlesticks == null || candlesticks.isEmpty()) {
			return Map.of();
		}

		List<Candlestick> sorted = candlesticks.stream()
				.sorted(Comparator.comparingLong(Candlestick::getTimestamp))
				.toList();

		if (sorted.size() < period) {
			return Map.of();
		}

		BarSeries series = BarSeriesFactory.fromCandlesticks(sorted, "MA");
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

		Indicator<Num> maIndicator = "SMA".equals(maType)
				? new SMAIndicator(closePrice, period)
				: new EMAIndicator(closePrice, period);

		Map<Long, Double> maByTimestamp = new HashMap<>();
		for (int i = period; i < sorted.size(); i++) {
			double maValue = maIndicator.getValue(i).doubleValue();
			if (Double.isNaN(maValue)) {
				continue;
			}
			maByTimestamp.put(sorted.get(i).getTimestamp(), maValue);
		}

		return Map.copyOf(maByTimestamp);
	}
}
