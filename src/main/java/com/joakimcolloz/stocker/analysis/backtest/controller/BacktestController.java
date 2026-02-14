package com.joakimcolloz.stocker.analysis.backtest.controller;

import com.joakimcolloz.stocker.analysis.dto.response.SupportedFilterInfo;
import com.joakimcolloz.stocker.analysis.filter.FilterType;
import com.joakimcolloz.stocker.analysis.pattern.PatternType;
import com.joakimcolloz.stocker.analysis.backtest.dto.BacktestRequest;
import com.joakimcolloz.stocker.analysis.backtest.dto.BacktestResult;
import com.joakimcolloz.stocker.analysis.backtest.service.BacktestService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing the backtest endpoint under {@code /api/analysis}.
 */
@RestController
@RequestMapping("/api/analysis")
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    @PostMapping("/backtest")
    @Operation(summary = "Run backtest",
            description = "Evaluates a trading strategy (entry conditions + exit rules) against historical data and returns trade list with performance metrics")
    public ResponseEntity<BacktestResult> runBacktest(@Valid @RequestBody BacktestRequest request) {
        BacktestResult result = backtestService.runBacktest(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/backtest/supported")
    @Operation(summary = "Get supported backtest options",
            description = "Returns supported values for backtest configuration (entryRule values, exitRule fields) and supported filter/pattern types")
    public ResponseEntity<Map<String, Object>> getSupportedBacktestOptions() {
        List<SupportedFilterInfo> filters = FilterType.allTypes().stream()
                .map(ft -> new SupportedFilterInfo(
                        ft.getCode(),
                        ft.getName(),
                        ft.getDescription(),
                        ft.getParamDescriptions()
                ))
                .toList();

        List<String> patternCodes = PatternType.supportedPatterns().stream()
                .map(p -> p.getCode())
                .toList();

        Map<String, Object> entryRule = Map.of(
                "entryMode", List.of("NEXT_BAR_OPEN", "LIMIT_ORDER"),
                "limitDirection", List.of("BELOW", "ABOVE")
        );

        Map<String, Object> exitRule = Map.of(
                "fields", List.of("stopLossPercent", "takeProfitPercent", "maxHoldingDays", "trailingStopLoss"),
                "notes", List.of(
                        "At least one of stopLossPercent, takeProfitPercent, maxHoldingDays must be provided",
                        "If trailingStopLoss=true then stopLossPercent is required"
                )
        );

        Map<String, Object> result = Map.of(
                "entryRule", entryRule,
                "exitRule", exitRule,
                "filters", filters,
                "patterns", patternCodes
        );

        return ResponseEntity.ok(result);
    }
}
