package com.joakimcolloz.stocker.analysis.backtest.service;

import com.joakimcolloz.stocker.analysis.backtest.dto.BacktestRequest;
import com.joakimcolloz.stocker.analysis.backtest.dto.BacktestResult;
import com.joakimcolloz.stocker.analysis.backtest.dto.ExecutionConfig;
import com.joakimcolloz.stocker.analysis.backtest.dto.ExecutionMode;
import com.joakimcolloz.stocker.analysis.backtest.dto.EntryRule;
import com.joakimcolloz.stocker.analysis.backtest.dto.Trade;
import com.joakimcolloz.stocker.analysis.backtest.data.MarketDataProvider;
import com.joakimcolloz.stocker.analysis.backtest.execution.BacktestSeriesInput;
import com.joakimcolloz.stocker.analysis.backtest.execution.BpsCostModel;
import com.joakimcolloz.stocker.analysis.backtest.execution.CostModel;
import com.joakimcolloz.stocker.analysis.backtest.execution.DailyExecutionModel;
import com.joakimcolloz.stocker.analysis.backtest.execution.ExecutionModel;
import com.joakimcolloz.stocker.analysis.backtest.execution.HybridIntradayExecutionModel;
import com.joakimcolloz.stocker.analysis.backtest.engine.BacktestEngine;
import com.joakimcolloz.stocker.analysis.backtest.engine.IndicatorContext;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterRequest;
import com.joakimcolloz.stocker.analysis.dto.filter.MacdFilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.MovingAverageFilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.PatternFilterParams;
import com.joakimcolloz.stocker.analysis.filter.FilterRegistry;
import com.joakimcolloz.stocker.analysis.indicator.MacdCalculator;
import com.joakimcolloz.stocker.analysis.indicator.MacdCalculator.MacdValues;
import com.joakimcolloz.stocker.analysis.indicator.MovingAverageCalculator;
import com.joakimcolloz.stocker.analysis.indicator.RsiCalculator;
import com.joakimcolloz.stocker.analysis.pattern.PatternDetector;
import com.joakimcolloz.stocker.analysis.pattern.PatternRegistry;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import com.joakimcolloz.stocker.api.entity.IntradayCandlestick;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestrates a backtest run: fetches candles (with extended lookback),
 * pre-computes indicators per symbol, delegates to {@link BacktestEngine},
 * and assembles the {@link BacktestResult}.
 */
@Service
public class BacktestService {

    private static final int DEFAULT_RSI_PERIOD = 14;
    private static final int DEFAULT_FAST_PERIOD = 12;
    private static final int DEFAULT_SLOW_PERIOD = 26;
    private static final int DEFAULT_SIGNAL_PERIOD = 9;

    private final MarketDataProvider marketDataProvider;
    private final FilterRegistry filterRegistry;
    private final PatternRegistry patternRegistry;
    private final BacktestEngine engine = new BacktestEngine();

    public BacktestService(MarketDataProvider marketDataProvider,
                           FilterRegistry filterRegistry,
                           PatternRegistry patternRegistry) {
        this.marketDataProvider = marketDataProvider;
        this.filterRegistry = filterRegistry;
        this.patternRegistry = patternRegistry;
    }

    private static final double DEFAULT_STARTING_CAPITAL = 10_000.0;

    /** Executes the backtest described by the request and returns aggregated results. */
    public BacktestResult runBacktest(BacktestRequest request) {
        List<FilterRequest> allConditions = allConditions(request);

        long maxLookback = computeMaxLookback(allConditions);
        long extendedStart = request.getStartTimestamp() - maxLookback;

        ExecutionConfig executionConfig = request.getExecution() != null
                ? request.getExecution()
                : new ExecutionConfig(null, null, null, null, null, null);

        List<String> symbols = request.getSymbols();
        if (symbols == null || symbols.isEmpty()) {
            symbols = marketDataProvider.fetchDistinctSymbols();
        }

        boolean needsRsi = needsIndicator(allConditions, "RSI");
        boolean needsMacd = needsIndicator(allConditions, "MACD");
        boolean needsVolume = needsIndicator(allConditions, "HIGH_VOLUME");
        boolean needsPattern = needsIndicator(allConditions, "PATTERN");
        boolean needsMa = needsIndicator(allConditions, "MA_CROSSOVER");

        double startingCapital = request.getStartingCapital() != null
                ? request.getStartingCapital() : DEFAULT_STARTING_CAPITAL;

        EntryRule entryRule = request.getEntryRule() != null
                ? request.getEntryRule() : new EntryRule("NEXT_BAR_OPEN", null, null, null, null);

        CostModel costModel = new BpsCostModel(
                executionConfig.slippageBps(),
                executionConfig.spreadBps(),
                executionConfig.commissionBps()
        );

        ExecutionModel executionModel = executionConfig.mode() == ExecutionMode.HYBRID_INTRADAY
                ? new HybridIntradayExecutionModel(engine)
                : new DailyExecutionModel(engine);

        List<Trade> allTrades = new ArrayList<>();

        for (String symbol : symbols) {
            List<String> singletonSymbol = List.of(symbol);
            List<Candlestick> sortedCandles = marketDataProvider.fetchDailyCandles(
                            singletonSymbol,
                            extendedStart,
                            request.getEndTimestamp()
                    ).stream()
                    .sorted(Comparator.comparingLong(Candlestick::getTimestamp))
                    .toList();

            if (sortedCandles.isEmpty()) {
                continue;
            }

            BacktestEngine.IntradayCandlesProvider intradayCandlesProvider = executionConfig.mode() == ExecutionMode.HYBRID_INTRADAY
                    ? (startInclusive, endExclusive) -> {
                        long endInclusive = Math.max(startInclusive, endExclusive - 1);
                        return marketDataProvider.fetchIntradayCandles(
                                singletonSymbol,
                                executionConfig.intradayInterval(),
                                startInclusive,
                                endInclusive
                        );
                    }
                    : null;

            IndicatorContext indicators = buildIndicatorContext(
                    sortedCandles, allConditions,
                    needsRsi, needsMacd, needsVolume, needsPattern, needsMa);

            BacktestSeriesInput input = new BacktestSeriesInput(
                    symbol,
                    sortedCandles,
                    intradayCandlesProvider,
                    request.getStartTimestamp(),
                    request.getEntryConditions(),
                    request.getExitRule(),
                    request.getExitConditions(),
                    indicators,
                    entryRule,
                    executionConfig
            );

            List<Trade> symbolTrades = executionModel.run(input, costModel);

            allTrades.addAll(symbolTrades);
        }

        allTrades.sort(Comparator.comparingLong(Trade::entryTimestamp));

        return BacktestResult.fromTrades(allTrades, request.getStartTimestamp(), request.getEndTimestamp(),
                startingCapital);
    }

    /** Combines entry + exit conditions, handling null exitConditions. */
    private List<FilterRequest> allConditions(BacktestRequest request) {
        if (request.getExitConditions() == null || request.getExitConditions().isEmpty()) {
            return request.getEntryConditions();
        }
        List<FilterRequest> all = new ArrayList<>(request.getEntryConditions());
        all.addAll(request.getExitConditions());
        return all;
    }

    private long computeMaxLookback(List<FilterRequest> conditions) {
        long maxLookback = 0;
        for (FilterRequest condition : conditions) {
            long lookback = filterRegistry.getFilter(condition.getType()).lookbackSeconds();
            maxLookback = Math.max(maxLookback, lookback);
        }
        return maxLookback;
    }

    private boolean needsIndicator(List<FilterRequest> conditions, String type) {
        return conditions.stream().anyMatch(c -> type.equals(c.getType()));
    }

    private IndicatorContext buildIndicatorContext(
            List<Candlestick> sortedCandles,
            List<FilterRequest> allConditions,
            boolean needsRsi,
            boolean needsMacd,
            boolean needsVolume,
            boolean needsPattern,
            boolean needsMa
    ) {
        Map<Long, Double> rsiValues = null;
        Map<Long, MacdValues> macdValues = null;
        double avgVolume = 0;
        Set<Long> patternTimestamps = null;
        Map<String, Map<Long, Double>> maValues = null;

        if (needsRsi) {
            rsiValues = RsiCalculator.calculateStableRsiByTimestamp(sortedCandles, DEFAULT_RSI_PERIOD);
        }

        if (needsMacd) {
            MacdFilterParams macdParams = findMacdParams(allConditions);
            int fast = macdParams != null && macdParams.getFastPeriod() != null
                    ? macdParams.getFastPeriod() : DEFAULT_FAST_PERIOD;
            int slow = macdParams != null && macdParams.getSlowPeriod() != null
                    ? macdParams.getSlowPeriod() : DEFAULT_SLOW_PERIOD;
            int signal = macdParams != null && macdParams.getSignalPeriod() != null
                    ? macdParams.getSignalPeriod() : DEFAULT_SIGNAL_PERIOD;
            macdValues = MacdCalculator.calculateStableMacdByTimestamp(sortedCandles, fast, slow, signal);
        }

        if (needsVolume) {
            avgVolume = sortedCandles.stream()
                    .mapToLong(Candlestick::getVolume)
                    .average()
                    .orElse(0);
        }

        if (needsPattern) {
            PatternFilterParams patternParams = findPatternParams(allConditions);
            String patternCode = patternParams.getPattern();
            PatternDetector detector = patternRegistry.findDetector(patternCode)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown pattern code: " + patternCode));
            patternTimestamps = detector.detect(sortedCandles).stream()
                    .map(match -> match.getTimestamp())
                    .collect(Collectors.toSet());
        }

        if (needsMa) {
            maValues = buildMaValues(sortedCandles, allConditions);
        }

        return new IndicatorContext(rsiValues, macdValues, avgVolume, patternTimestamps, maValues);
    }

    /** Collects all unique (maType, period) pairs from MA_CROSSOVER conditions and computes each. */
    private Map<String, Map<Long, Double>> buildMaValues(
            List<Candlestick> sortedCandles,
            List<FilterRequest> allConditions
    ) {
        // Collect unique (maType, period) pairs
        Set<String> keys = new LinkedHashSet<>();
        for (FilterRequest condition : allConditions) {
            if ("MA_CROSSOVER".equals(condition.getType())) {
                MovingAverageFilterParams maParams = (MovingAverageFilterParams) condition.getParams();
                String key = maParams.getMaType() + "_" + maParams.getPeriod();
                keys.add(key);
            }
        }

        Map<String, Map<Long, Double>> result = new HashMap<>();
        for (String key : keys) {
            String[] parts = key.split("_", 2);
            String maType = parts[0];
            int period = Integer.parseInt(parts[1]);
            Map<Long, Double> values = MovingAverageCalculator.calculateByTimestamp(sortedCandles, period, maType);
            result.put(key, values);
        }
        return result;
    }

    private MacdFilterParams findMacdParams(List<FilterRequest> conditions) {
        return conditions.stream()
                .filter(c -> "MACD".equals(c.getType()))
                .map(c -> (MacdFilterParams) c.getParams())
                .findFirst()
                .orElse(null);
    }

    private PatternFilterParams findPatternParams(List<FilterRequest> conditions) {
        return conditions.stream()
                .filter(c -> "PATTERN".equals(c.getType()))
                .map(c -> (PatternFilterParams) c.getParams())
                .findFirst()
                .orElse(null);
    }
}
