package com.joakimcolloz.stocker.analysis.backtest.execution;

import com.joakimcolloz.stocker.analysis.backtest.dto.ExecutionConfig;
import com.joakimcolloz.stocker.analysis.backtest.dto.EntryRule;
import com.joakimcolloz.stocker.analysis.backtest.dto.ExitRule;
import com.joakimcolloz.stocker.analysis.backtest.engine.IndicatorContext;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterRequest;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import com.joakimcolloz.stocker.api.entity.IntradayCandlestick;

import java.util.List;

public record BacktestSeriesInput(
        String symbol,
        List<Candlestick> dailyCandles,
        List<IntradayCandlestick> intradayCandles,
        long tradingStartTimestamp,
        List<FilterRequest> entryConditions,
        ExitRule exitRule,
        List<FilterRequest> exitConditions,
        IndicatorContext indicators,
        EntryRule entryRule,
        ExecutionConfig executionConfig
) {
}
