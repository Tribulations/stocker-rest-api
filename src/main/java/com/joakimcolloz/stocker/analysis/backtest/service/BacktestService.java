package com.joakimcolloz.stocker.analysis.backtest.service;

import com.joakimcolloz.stocker.analysis.backtest.dto.BacktestRequest;
import com.joakimcolloz.stocker.analysis.backtest.dto.BacktestResult;
import com.joakimcolloz.stocker.analysis.backtest.dto.EntryRule;
import com.joakimcolloz.stocker.analysis.backtest.dto.Trade;
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
import com.joakimcolloz.stocker.api.dao.CandlestickRepository;
import com.joakimcolloz.stocker.api.entity.Candlestick;
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

    private final CandlestickRepository candlestickRepository;
    private final FilterRegistry filterRegistry;
    private final PatternRegistry patternRegistry;
    private final BacktestEngine engine = new BacktestEngine();

    public BacktestService(CandlestickRepository candlestickRepository,
                           FilterRegistry filterRegistry,
                           PatternRegistry patternRegistry) {
        this.candlestickRepository = candlestickRepository;
        this.filterRegistry = filterRegistry;
        this.patternRegistry = patternRegistry;
    }

    private static final double DEFAULT_STARTING_CAPITAL = 10_000.0;

    /** Executes the backtest described by the request and returns aggregated results. */
    public BacktestResult runBacktest(BacktestRequest request) {
        List<FilterRequest> allConditions = allConditions(request);

        long maxLookback = computeMaxLookback(allConditions);
        long extendedStart = request.getStartTimestamp() - maxLookback;

        List<Candlestick> allCandles = fetchCandles(request.getSymbols(), extendedStart, request.getEndTimestamp());

        Map<String, List<Candlestick>> bySymbol = allCandles.stream()
                .collect(Collectors.groupingBy(Candlestick::getSymbol));

        boolean needsRsi = needsIndicator(allConditions, "RSI");
        boolean needsMacd = needsIndicator(allConditions, "MACD");
        boolean needsVolume = needsIndicator(allConditions, "HIGH_VOLUME");
        boolean needsPattern = needsIndicator(allConditions, "PATTERN");
        boolean needsMa = needsIndicator(allConditions, "MA_CROSSOVER");

        double startingCapital = request.getStartingCapital() != null
                ? request.getStartingCapital() : DEFAULT_STARTING_CAPITAL;

        EntryRule entryRule = request.getEntryRule() != null
                ? request.getEntryRule() : new EntryRule("NEXT_BAR_OPEN", null, null, null);

        List<Trade> allTrades = new ArrayList<>();

        for (Map.Entry<String, List<Candlestick>> entry : bySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<Candlestick> sortedCandles = entry.getValue().stream()
                    .sorted(Comparator.comparingLong(Candlestick::getTimestamp))
                    .toList();

            IndicatorContext indicators = buildIndicatorContext(
                    sortedCandles, allConditions,
                    needsRsi, needsMacd, needsVolume, needsPattern, needsMa);

            List<Trade> symbolTrades = engine.runForSymbol(
                    symbol, sortedCandles, request.getStartTimestamp(),
                    request.getEntryConditions(), request.getExitRule(),
                    request.getExitConditions(), indicators, entryRule);

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

    private List<Candlestick> fetchCandles(List<String> symbols, long startTimestamp, long endTimestamp) {
        if (symbols != null && !symbols.isEmpty()) {
            List<Candlestick> result = new ArrayList<>();
            for (String symbol : symbols) {
                result.addAll(candlestickRepository.findBySymbolAndTimestampBetween(
                        symbol, startTimestamp, endTimestamp));
            }
            return result;
        }
        return candlestickRepository.findByTimestampBetween(startTimestamp, endTimestamp);
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
