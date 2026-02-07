package com.joakimcolloz.stocker.analysis.controller;

import com.joakimcolloz.stocker.analysis.dto.request.CombinedScanRequest;
import com.joakimcolloz.stocker.analysis.dto.response.CombinedFilterResult;
import com.joakimcolloz.stocker.analysis.service.CombinedScanService;
import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for combined filter scanning.
 * <p>
 * Accepts a set of filters via {@code POST /api/analysis/combined-scan} and applies them
 * sequentially to find candlesticks matching all criteria. Each result includes per-filter
 * evidence describing why the candlestick matched.
 */
@RestController
@RequestMapping("/api/analysis")
public class CombinedScanController {

    private final CombinedScanService combinedScanService;

    public CombinedScanController(CombinedScanService combinedScanService) {
        this.combinedScanService = combinedScanService;
    }

    /**
     * Executes a combined filter scan.
     *
     * @param request the scan request containing filters and time range
     * @return list of candlesticks that passed all filters, enriched with filter evidence
     */
    @PostMapping("/combined-scan")
    @Operation(summary = "Execute combined filter scan",
            description = "Applies multiple filters sequentially to find candlesticks matching all criteria")
    public ResponseEntity<List<CombinedFilterResult>> combinedScan(@Valid @RequestBody CombinedScanRequest request) {
        List<CombinedFilterResult> results = combinedScanService.executeScan(request);
        return ResponseEntity.ok(results);
    }
}
