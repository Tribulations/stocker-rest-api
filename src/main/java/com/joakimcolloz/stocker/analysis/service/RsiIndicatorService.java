package com.joakimcolloz.stocker.analysis.service;

import com.joakimcolloz.stocker.analysis.dto.response.RsiPoint;
import com.joakimcolloz.stocker.analysis.dto.response.RsiSeriesResponse;
import com.joakimcolloz.stocker.analysis.indicator.RsiCalculator;
import com.joakimcolloz.stocker.api.dao.CandlestickRepository;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Service that produces stable RSI time-series data for a given symbol.
 * <p>
 * "Stable" means the lookback window is extended beyond the requested range so that
 * the initial RSI values have converged and are not distorted by insufficient history.
 *
 * @see RsiCalculator
 */
@Service
public class RsiIndicatorService {
	private final CandlestickRepository candlestickRepository;

	public RsiIndicatorService(CandlestickRepository candlestickRepository) {
		this.candlestickRepository = candlestickRepository;
	}

	/**
	 * Computes a stable RSI series for the requested window.
	 * <p>
	 * Fetches extra candlesticks before {@code startTimestamp} (determined by
	 * {@link RsiCalculator#lookbackSecondsForStableRsi}) so the returned RSI values
	 * are fully converged. Only data points within [{@code startTimestamp}, {@code endTimestamp}]
	 * are included in the response.
	 *
	 * @param symbol         ticker symbol
	 * @param startTimestamp epoch-second start of the visible range
	 * @param endTimestamp   epoch-second end of the visible range
	 * @param period         RSI period (must be positive)
	 * @return response containing the RSI data points
	 * @throws IllegalArgumentException if parameters are invalid or data is insufficient
	 */
	public RsiSeriesResponse getStableRsiSeries(String symbol, long startTimestamp, long endTimestamp, int period) {
		if (symbol == null || symbol.isBlank()) {
			throw new IllegalArgumentException("Symbol is required");
		}
		if (startTimestamp <= 0 || endTimestamp <= 0 || startTimestamp > endTimestamp) {
			throw new IllegalArgumentException("Invalid time range");
		}
		if (period <= 0) {
			throw new IllegalArgumentException("RSI period must be positive");
		}

		long lookbackSeconds = RsiCalculator.lookbackSecondsForStableRsi(period);
		long extendedStart = Math.max(1L, startTimestamp - lookbackSeconds);

		List<Candlestick> candles = candlestickRepository.findBySymbolAndTimestampBetween(symbol, extendedStart, endTimestamp);
		candles = candles.stream().sorted(Comparator.comparingLong(Candlestick::getTimestamp)).toList();

		Map<Long, Double> rsiByTimestamp = RsiCalculator.calculateStableRsiByTimestampOrThrow(candles, period);

		List<RsiPoint> points = new ArrayList<>();
		for (Candlestick candle : candles) {
			long ts = candle.getTimestamp();
			if (ts < startTimestamp || ts > endTimestamp) {
				continue;
			}
			Double rsi = rsiByTimestamp.get(ts);
			if (rsi != null) {
				points.add(new RsiPoint(ts, rsi));
			}
		}

		return new RsiSeriesResponse(symbol, period, startTimestamp, endTimestamp, points);
	}
}
