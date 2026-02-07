package com.joakimcolloz.stocker.analysis.controller;

import com.joakimcolloz.stocker.analysis.dto.response.MacdSeriesResponse;
import com.joakimcolloz.stocker.analysis.dto.response.RsiSeriesResponse;
import com.joakimcolloz.stocker.analysis.service.MacdIndicatorService;
import com.joakimcolloz.stocker.analysis.service.RsiIndicatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing technical indicator endpoints.
 * <p>
 * Provides time-series indicator data (e.g. RSI) for a given symbol and time range.
 * All endpoints are mapped under {@code /api/analysis/indicators}.
 */
@RestController
@RequestMapping("/api/analysis/indicators")
@Tag(name = "Indicators", description = "Technical indicator endpoints")
public class IndicatorsController {
	private final RsiIndicatorService rsiIndicatorService;
	private final MacdIndicatorService macdIndicatorService;

	public IndicatorsController(RsiIndicatorService rsiIndicatorService, MacdIndicatorService macdIndicatorService) {
		this.rsiIndicatorService = rsiIndicatorService;
		this.macdIndicatorService = macdIndicatorService;
	}

	/**
	 * Returns a stable RSI series for the given symbol and time range.
	 *
	 * @param symbol         ticker symbol to query
	 * @param startTimestamp epoch-second start of the requested range
	 * @param endTimestamp   epoch-second end of the requested range
	 * @param period         RSI period (default 14)
	 * @return RSI data points within the requested window
	 */
	@GetMapping("/rsi")
	@Operation(summary = "Get RSI series", description = "Returns stable RSI values for a symbol within the requested time range")
	public ResponseEntity<RsiSeriesResponse> getRsiSeries(
			@RequestParam String symbol,
			@RequestParam long startTimestamp,
			@RequestParam long endTimestamp,
			@RequestParam(defaultValue = "14") int period
	) {
		return ResponseEntity.ok(rsiIndicatorService.getStableRsiSeries(symbol, startTimestamp, endTimestamp, period));
	}

	/**
	 * Returns a stable MACD series for the given symbol and time range.
	 *
	 * @param symbol         ticker symbol to query
	 * @param startTimestamp epoch-second start of the requested range
	 * @param endTimestamp   epoch-second end of the requested range
	 * @param fastPeriod     MACD fast period (default 12)
	 * @param slowPeriod     MACD slow period (default 26)
	 * @param signalPeriod   MACD signal period (default 9)
	 * @return MACD data points within the requested window
	 */
	@GetMapping("/macd")
	@Operation(summary = "Get MACD series", description = "Returns stable MACD values for a symbol within the requested time range")
	public ResponseEntity<MacdSeriesResponse> getMacdSeries(
			@RequestParam String symbol,
			@RequestParam long startTimestamp,
			@RequestParam long endTimestamp,
			@RequestParam(defaultValue = "12") int fastPeriod,
			@RequestParam(defaultValue = "26") int slowPeriod,
			@RequestParam(defaultValue = "9") int signalPeriod
	) {
		return ResponseEntity.ok(macdIndicatorService.getStableMacdSeries(
				symbol, startTimestamp, endTimestamp, fastPeriod, slowPeriod, signalPeriod));
	}
}
