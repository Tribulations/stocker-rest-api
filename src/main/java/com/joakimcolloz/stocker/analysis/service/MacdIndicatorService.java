package com.joakimcolloz.stocker.analysis.service;

import com.joakimcolloz.stocker.analysis.dto.response.MacdPoint;
import com.joakimcolloz.stocker.analysis.dto.response.MacdSeriesResponse;
import com.joakimcolloz.stocker.analysis.indicator.MacdCalculator;
import com.joakimcolloz.stocker.analysis.indicator.MacdCalculator.MacdValues;
import com.joakimcolloz.stocker.api.dao.CandlestickRepository;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Service that produces stable MACD time-series data for a given symbol.
 * <p>
 * "Stable" means the lookback window is extended beyond the requested range so that
 * the initial MACD values have converged and are not distorted by insufficient history.
 *
 * @see MacdCalculator
 */
@Service
public class MacdIndicatorService {
	private final CandlestickRepository candlestickRepository;

	public MacdIndicatorService(CandlestickRepository candlestickRepository) {
		this.candlestickRepository = candlestickRepository;
	}

	/**
	 * Computes a stable MACD series for the requested window.
	 * <p>
	 * Fetches extra candlesticks before {@code startTimestamp} (determined by
	 * {@link MacdCalculator#lookbackSecondsForStableMacd}) so the returned MACD values
	 * are fully converged. Only data points within [{@code startTimestamp}, {@code endTimestamp}]
	 * are included in the response.
	 *
	 * @param symbol         ticker symbol
	 * @param startTimestamp epoch-second start of the visible range
	 * @param endTimestamp   epoch-second end of the visible range
	 * @param fastPeriod     MACD fast period (must be positive)
	 * @param slowPeriod     MACD slow period (must be positive, greater than fastPeriod)
	 * @param signalPeriod   MACD signal period (must be positive)
	 * @return response containing the MACD data points
	 * @throws IllegalArgumentException if parameters are invalid or data is insufficient
	 */
	public MacdSeriesResponse getStableMacdSeries(String symbol, long startTimestamp, long endTimestamp,
	                                               int fastPeriod, int slowPeriod, int signalPeriod) {
		if (symbol == null || symbol.isBlank()) {
			throw new IllegalArgumentException("Symbol is required");
		}
		if (startTimestamp <= 0 || endTimestamp <= 0 || startTimestamp > endTimestamp) {
			throw new IllegalArgumentException("Invalid time range");
		}
		if (fastPeriod <= 0 || slowPeriod <= 0 || signalPeriod <= 0) {
			throw new IllegalArgumentException("MACD periods must be positive");
		}
		if (fastPeriod >= slowPeriod) {
			throw new IllegalArgumentException("Fast period must be less than slow period");
		}

		long lookbackSeconds = MacdCalculator.lookbackSecondsForStableMacd(slowPeriod);
		long extendedStart = Math.max(1L, startTimestamp - lookbackSeconds);

		List<Candlestick> candles = candlestickRepository.findBySymbolAndTimestampBetween(symbol, extendedStart, endTimestamp);
		candles = candles.stream().sorted(Comparator.comparingLong(Candlestick::getTimestamp)).toList();

		Map<Long, MacdValues> macdByTimestamp = MacdCalculator.calculateStableMacdByTimestampOrThrow(
				candles, fastPeriod, slowPeriod, signalPeriod);

		List<MacdPoint> points = new ArrayList<>();
		for (Candlestick candle : candles) {
			long ts = candle.getTimestamp();
			if (ts < startTimestamp || ts > endTimestamp) {
				continue;
			}
			MacdValues values = macdByTimestamp.get(ts);
			if (values != null) {
				points.add(new MacdPoint(ts, values.macd(), values.signal(), values.histogram()));
			}
		}

		return new MacdSeriesResponse(symbol, fastPeriod, slowPeriod, signalPeriod, startTimestamp, endTimestamp, points);
	}
}
