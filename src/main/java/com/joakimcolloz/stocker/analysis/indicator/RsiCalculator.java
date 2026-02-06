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

public final class RsiCalculator {
	private static final long SECONDS_PER_DAY = 86400L;

	private RsiCalculator() {
	}

	public static long lookbackSecondsForStableRsi(int period) {
		if (period <= 0) {
			throw new IllegalArgumentException("RSI period must be positive");
		}
		return (long) period * 2L * SECONDS_PER_DAY;
	}

	public static Map<Long, Double> calculateStableRsiByTimestampOrThrow(List<Candlestick> candlesticks, int period) {
		Map<Long, Double> result = calculateStableRsiByTimestamp(candlesticks, period);
		if (result.isEmpty()) {
			throw new IllegalArgumentException("Not enough candlesticks to calculate stable RSI");
		}
		return result;
	}

	public static Map<Long, Double> calculateRsiByTimestamp(List<Candlestick> candlesticks, int period) {
		return calculateRsiByTimestamp(candlesticks, period, period + 1);
	}

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
