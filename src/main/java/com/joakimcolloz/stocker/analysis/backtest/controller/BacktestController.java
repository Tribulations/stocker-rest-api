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
                        "If trailingStopLoss=true then stopLossPercent is required",
                        "Trailing stop updates after bar close using close price (conservative)"
                )
        );

        Map<String, Object> execution = Map.of(
                "mode", List.of("DAILY_ONLY", "HYBRID_INTRADAY"),
                "intradayInterval", List.of("5m"),
                "fields", List.of("slippageBps", "spreadBps", "commissionBps", "conservativeFillMode"),
                "defaults", Map.of(
                        "mode", "DAILY_ONLY",
                        "intradayInterval", "5m",
                        "slippageBps", 0.0,
                        "spreadBps", 0.0,
                        "commissionBps", 0.0,
                        "conservativeFillMode", true
                )
        );

        Map<String, Object> result = Map.of(
                "entryRule", entryRule,
                "exitRule", exitRule,
                "execution", execution,
                "filters", filters,
                "patterns", patternCodes
        );

        return ResponseEntity.ok(result);
    }

    @GetMapping("/backtest/description")
    @Operation(summary = "Backtest framework description",
            description = "Explains the execution model assumptions, conservative fill rules, intraday fallback behavior, and cost/friction parameters")
    public ResponseEntity<Map<String, Object>> getBacktestDescription() {
        Map<String, Object> executionModes = Map.of(
                "DAILY_ONLY", "Stops/targets/trailing evaluated using daily OHLC with conservative same-bar rules",
                "HYBRID_INTRADAY", "Entries still driven by daily signals; exits are evaluated on intraday 5m OHLC when available, otherwise fall back to daily"
        );

        Map<String, Object> conservativeRules = Map.of(
                "stopFirstOnSameBar", true,
                "gapThroughStopWorstCase", "If price gaps beyond stop on bar open, fill uses worst-case (e.g., min(open, low) for long stops)",
                "trailingStopUpdate", "Trailing stop high-water-mark updates after bar close using close price (daily and intraday)"
        );

        Map<String, Object> costs = Map.of(
                "spreadBps", "Applied as half-spread on entry and half-spread on exit",
                "slippageBps", "Applied against the trader (entry worse, exit worse)",
                "commissionBps", "Applied on both entry and exit when computing return%"
        );

        Map<String, Object> result = Map.of(
                "positions", "LONG_ONLY",
                "executionModes", executionModes,
                "conservativeRules", conservativeRules,
                "costs", costs,
                "intraday", Map.of(
                        "interval", "5m",
                        "fallback", "If intraday bars are missing for a given day, that day uses DAILY_ONLY execution"
                )
        );

        return ResponseEntity.ok(result);
    }
}
