package com.joakimcolloz.stocker.analysis.backtest.data;

import com.joakimcolloz.stocker.api.entity.Candlestick;
import com.joakimcolloz.stocker.api.entity.IntradayCandlestick;

import java.util.List;

public interface MarketDataProvider {
    List<String> fetchDistinctSymbols();

    List<Candlestick> fetchDailyCandles(List<String> symbols, long startTimestamp, long endTimestamp);

    List<IntradayCandlestick> fetchIntradayCandles(List<String> symbols, String interval, long startTimestamp, long endTimestamp);
}
