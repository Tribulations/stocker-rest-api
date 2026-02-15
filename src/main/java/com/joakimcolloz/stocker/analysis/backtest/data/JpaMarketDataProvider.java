package com.joakimcolloz.stocker.analysis.backtest.data;

import com.joakimcolloz.stocker.api.dao.CandlestickRepository;
import com.joakimcolloz.stocker.api.dao.IntradayCandlestickRepository;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import com.joakimcolloz.stocker.api.entity.IntradayCandlestick;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JpaMarketDataProvider implements MarketDataProvider {

    private final CandlestickRepository candlestickRepository;
    private final IntradayCandlestickRepository intradayCandlestickRepository;

    public JpaMarketDataProvider(
            CandlestickRepository candlestickRepository,
            IntradayCandlestickRepository intradayCandlestickRepository
    ) {
        this.candlestickRepository = candlestickRepository;
        this.intradayCandlestickRepository = intradayCandlestickRepository;
    }

    @Override
    public List<String> fetchDistinctSymbols() {
        return candlestickRepository.findAllDistinctSymbols();
    }

    @Override
    public List<Candlestick> fetchDailyCandles(List<String> symbols, long startTimestamp, long endTimestamp) {
        if (symbols != null && !symbols.isEmpty()) {
            List<Candlestick> result = new ArrayList<>();
            for (String symbol : symbols) {
                result.addAll(candlestickRepository.findBySymbolAndTimestampBetween(symbol, startTimestamp, endTimestamp));
            }
            return result;
        }
        return candlestickRepository.findByTimestampBetween(startTimestamp, endTimestamp);
    }

    @Override
    public List<IntradayCandlestick> fetchIntradayCandles(List<String> symbols, String interval, long startTimestamp, long endTimestamp) {
        if (symbols != null && !symbols.isEmpty()) {
            return intradayCandlestickRepository.findBySymbolInAndIntervalAndTimestampBetweenOrderBySymbolAscTimestampAsc(
                    symbols, interval, startTimestamp, endTimestamp);
        }
        return intradayCandlestickRepository.findByIntervalAndTimestampBetweenOrderBySymbolAscTimestampAsc(
                interval, startTimestamp, endTimestamp);
    }
}
