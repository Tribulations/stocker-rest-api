package com.joakimcolloz.stocker.analysis.controller;

import com.joakimcolloz.stocker.analysis.dto.response.RsiSeriesResponse;
import com.joakimcolloz.stocker.analysis.service.RsiIndicatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis/indicators")
@Tag(name = "Indicators", description = "Technical indicator endpoints")
public class IndicatorsController {
	private final RsiIndicatorService rsiIndicatorService;

	public IndicatorsController(RsiIndicatorService rsiIndicatorService) {
		this.rsiIndicatorService = rsiIndicatorService;
	}

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
}
