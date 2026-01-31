package com.joakimcolloz.stocker.analysis.controller;

import com.joakimcolloz.stocker.analysis.dto.AverageVolumeStatistic;
import com.joakimcolloz.stocker.analysis.dto.HighVolumeCandlestick;
import com.joakimcolloz.stocker.analysis.dto.HighVolumeRequest;
import com.joakimcolloz.stocker.analysis.dto.PatternInfo;
import com.joakimcolloz.stocker.analysis.dto.PatternMatch;
import com.joakimcolloz.stocker.analysis.dto.ScanRequest;
import com.joakimcolloz.stocker.analysis.dto.ScanResponse;
import com.joakimcolloz.stocker.analysis.service.AnalysisService;

import com.joakimcolloz.stocker.api.entity.Candlestick;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for candlestick pattern analysis.
 * <p>
 * Provides endpoints to:
 * <ul>
 *   <li>Scan for patterns across all stocks in a time range</li>
 *   <li>List supported pattern types</li>
 * </ul>
 *
 * @author Joakim Colloz
 * @version 1.0
 */
@RestController
@RequestMapping("/api/analysis")
@Tag(name = "Pattern Analysis", description = "Candlestick pattern detection endpoints")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * Scans for candlestick patterns across all stocks in the specified time range.
     *
     * @param request scan request with patterns and time range
     * @return response containing detected pattern matches
     */
    @PostMapping("/scan")
    @Operation(summary = "Scan for patterns",
            description = "Analyzes all stocks in the given time range for selected patterns")
    public ResponseEntity<ScanResponse> scanPatterns(@Valid @RequestBody ScanRequest request) {
        // Perform analysis
        List<PatternMatch> matches = analysisService.scanPatterns(request);

        // Return results
        ScanResponse response = new ScanResponse(matches);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns a list of all supported pattern types.
     *
     * @return list of pattern information
     */
    @GetMapping("/patterns/supported")
    @Operation(summary = "Get supported patterns",
            description = "Returns all available pattern types that can be detected")
    public ResponseEntity<List<PatternInfo>> getSupportedPatterns() {
        List<PatternInfo> patterns = List.of(
                new PatternInfo(
                        "HAMMER",
                        "Hammer",
                        "Bullish reversal with long lower shadow",
                        "BULLISH"
                )
                // Add more patterns here as they're implemented
        );

        return ResponseEntity.ok(patterns);
    }

    /**
     * Calculates average trading volume for all stock symbols within the specified time range.
     *
     * @return list of average volume statistics
     */
    @GetMapping("/statistics/average-volume")
    @Operation(summary = "Get average volumes",
            description = "Returns average trading volume statistics for each stock symbol within the specified time range")
    public ResponseEntity<List<AverageVolumeStatistic>> getAverageVolumes(
            @RequestParam long startTimestamp,
            @RequestParam long endTimestamp) {

        // Validate time range
        if (startTimestamp <= 0 || endTimestamp <= 0 || startTimestamp > endTimestamp) {
            return ResponseEntity.badRequest().build();
        }

        List<AverageVolumeStatistic> statistics = analysisService.calculateAverageVolumes(
                startTimestamp, endTimestamp
        );

        return ResponseEntity.ok(statistics);
    }

    @GetMapping("/symbols")
    public List<String> findAllDistinctSymbols() {
        return analysisService.findAllDistinctSymbols();
    }

    @GetMapping("/symbol/{symbol}/range")
    public List<Candlestick> getCandlesticksBySymbolAndTimeRange(
            @PathVariable String symbol,
            @RequestParam long startTimestamp,
            @RequestParam long endTimestamp) {
        return analysisService.findBySymbolAndTimestampBetween(
                symbol, startTimestamp, endTimestamp);
    }

    /**
     * Identifies candlesticks with volume significantly above average.
     *
     * @param request request containing time range and percentage threshold
     * @return list of high volume candlesticks
     */
    @PostMapping("/high-volume")
    @Operation(summary = "Find high volume candlesticks",
            description = "Identifies candlesticks where volume exceeds the average by a specified percentage within the time range")
    public ResponseEntity<List<HighVolumeCandlestick>> findHighVolume(@Valid @RequestBody HighVolumeRequest request) {
        // Find high volume candlesticks
        List<HighVolumeCandlestick> results = analysisService.findHighVolumeCandlesticks(request);

        return ResponseEntity.ok(results);
    }
}
