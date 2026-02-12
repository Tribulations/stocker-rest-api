package com.joakimcolloz.stocker.analysis.backtest.service;

import com.joakimcolloz.stocker.analysis.backtest.dto.BacktestRequest;
import com.joakimcolloz.stocker.analysis.backtest.dto.BacktestResult;
import com.joakimcolloz.stocker.analysis.backtest.dto.Trade;
import com.joakimcolloz.stocker.analysis.backtest.engine.BacktestEngine;
import com.joakimcolloz.stocker.analysis.backtest.engine.IndicatorContext;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterRequest;
import com.joakimcolloz.stocker.analysis.dto.filter.HighVolumeFilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.MacdFilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.RsiFilterParams;
import com.joakimcolloz.stocker.analysis.filter.FilterRegistry;
import com.joakimcolloz.stocker.analysis.indicator.MacdCalculator;
import com.joakimcolloz.stocker.analysis.indicator.MacdCalculator.MacdValues;
import com.joakimcolloz.stocker.analysis.indicator.RsiCalculator;
import com.joakimcolloz.stocker.api.dao.CandlestickRepository;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BacktestService {

    private static final int DEFAULT_RSI_PERIOD = 14;
    private static final int DEFAULT_FAST_PERIOD = 12;
    private static final int DEFAULT_SLOW_PERIOD = 26;
    private static final int DEFAULT_SIGNAL_PERIOD = 9;

    private final CandlestickRepository candlestickRepository;
    private final FilterRegistry filterRegistry;
    private final BacktestEngine engine = new BacktestEngine();

    public BacktestService(CandlestickRepository candlestickRepository, FilterRegistry filterRegistry) {
        this.candlestickRepository = candlestickRepository;
        this.filterRegistry = filterRegistry;
    }

    public BacktestResult runBacktest(BacktestRequest request) {
        long maxLookback = computeMaxLookback(request.getEntryConditions());
        long extendedStart = request.getStartTimestamp() - maxLookback;

        List<Candlestick> allCandles = fetchCandles(request.getSymbols(), extendedStart, request.getEndTimestamp());

        Map<String, List<Candlestick>> bySymbol = allCandles.stream()
                .collect(Collectors.groupingBy(Candlestick::getSymbol));

        boolean needsRsi = needsIndicator(request.getEntryConditions(), "RSI");
        boolean needsMacd = needsIndicator(request.getEntryConditions(), "MACD");
        boolean needsVolume = needsIndicator(request.getEntryConditions(), "HIGH_VOLUME");

        List<Trade> allTrades = new ArrayList<>();

        for (Map.Entry<String, List<Candlestick>> entry : bySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<Candlestick> sortedCandles = entry.getValue().stream()
                    .sorted(Comparator.comparingLong(Candlestick::getTimestamp))
                    .toList();

            IndicatorContext indicators = buildIndicatorContext(
                    sortedCandles, request.getEntryConditions(), needsRsi, needsMacd, needsVolume);

            List<Trade> symbolTrades = engine.runForSymbol(
                    symbol, sortedCandles, request.getStartTimestamp(),
                    request.getEntryConditions(), request.getExitRule(), indicators);

            allTrades.addAll(symbolTrades);
        }

        allTrades.sort(Comparator.comparingLong(Trade::entryTimestamp));

        return BacktestResult.fromTrades(allTrades, request.getStartTimestamp(), request.getEndTimestamp());
    }

    private long computeMaxLookback(List<FilterRequest> entryConditions) {
        long maxLookback = 0;
        for (FilterRequest condition : entryConditions) {
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
            List<FilterRequest> entryConditions,
            boolean needsRsi,
            boolean needsMacd,
            boolean needsVolume
    ) {
        Map<Long, Double> rsiValues = null;
        Map<Long, MacdValues> macdValues = null;
        double avgVolume = 0;

        if (needsRsi) {
            rsiValues = RsiCalculator.calculateStableRsiByTimestamp(sortedCandles, DEFAULT_RSI_PERIOD);
        }

        if (needsMacd) {
            MacdFilterParams macdParams = findMacdParams(entryConditions);
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

        return new IndicatorContext(rsiValues, macdValues, avgVolume);
    }

    private MacdFilterParams findMacdParams(List<FilterRequest> conditions) {
        return conditions.stream()
                .filter(c -> "MACD".equals(c.getType()))
                .map(c -> (MacdFilterParams) c.getParams())
                .findFirst()
                .orElse(null);
    }
}
