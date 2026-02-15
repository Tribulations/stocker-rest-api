package com.joakimcolloz.stocker.analysis.backtest;

import com.joakimcolloz.stocker.analysis.backtest.dto.ExecutionConfig;
import com.joakimcolloz.stocker.analysis.backtest.dto.ExecutionMode;
import com.joakimcolloz.stocker.analysis.backtest.dto.EntryRule;
import com.joakimcolloz.stocker.analysis.backtest.dto.ExitRule;
import com.joakimcolloz.stocker.analysis.backtest.dto.Trade;
import com.joakimcolloz.stocker.analysis.backtest.engine.BacktestEngine;
import com.joakimcolloz.stocker.analysis.backtest.engine.IndicatorContext;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterRequest;
import com.joakimcolloz.stocker.analysis.dto.filter.RsiFilterParams;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import com.joakimcolloz.stocker.api.entity.IntradayCandlestick;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Hybrid Intraday Execution Unit Tests")
class HybridIntradayExecutionTest {

    private static final long BASE_TS = 1_704_067_200L;
    private static final long DAY = 86_400L;

    private Candlestick candle(long ts, double open, double high, double low, double close) {
        Candlestick c = new Candlestick();
        c.setSymbol("TEST.ST");
        c.setTimestamp(ts);
        c.setOpen(open);
        c.setHigh(high);
        c.setLow(low);
        c.setClose(close);
        c.setVolume(1_000L);
        return c;
    }

    private IntradayCandlestick iCandle(long ts, double open, double high, double low, double close) {
        IntradayCandlestick c = new IntradayCandlestick();
        c.setSymbol("TEST.ST");
        c.setInterval("5m");
        c.setTimestamp(ts);
        c.setOpen(open);
        c.setHigh(high);
        c.setLow(low);
        c.setClose(close);
        c.setVolume(1_000L);
        return c;
    }

    private FilterRequest rsiEntry(double threshold, String condition) {
        FilterRequest req = new FilterRequest();
        req.setType("RSI");
        RsiFilterParams params = new RsiFilterParams();
        params.setThreshold(threshold);
        params.setCondition(condition);
        req.setParams(params);
        return req;
    }

    @Test
    @DisplayName("When stop and take-profit hit in same 5m bar, stop wins (conservative)")
    void stopWinsOnSameIntradayBar() {
        BacktestEngine engine = new BacktestEngine();

        List<Candlestick> daily = List.of(
                candle(BASE_TS, 100, 101, 99, 100),
                candle(BASE_TS + DAY, 100, 101, 99, 100),
                candle(BASE_TS + 2 * DAY, 100, 101, 99, 100)
        );

        Map<Long, Double> rsi = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 50.0);
        IndicatorContext ctx = new IndicatorContext(rsi, null, 0, null, null);

        ExitRule exitRule = new ExitRule(5.0, 5.0, null, null);
        EntryRule entryRule = new EntryRule("NEXT_BAR_OPEN", null, null, null, null);

        ExecutionConfig cfg = new ExecutionConfig(ExecutionMode.HYBRID_INTRADAY, "5m", 0.0, 0.0, 0.0, true);

        // Entry happens on day1 open (100). Intraday bar hits high>=105 and low<=95.
        List<IntradayCandlestick> intraday = List.of(
                iCandle(BASE_TS + DAY + 60, 100, 106, 94, 100)
        );

        List<Trade> trades = engine.runForSymbol(
                "TEST.ST",
                daily,
                BASE_TS,
                List.of(rsiEntry(30, "below")),
                exitRule,
                null,
                ctx,
                entryRule,
                cfg,
                intraday
        );

        assertEquals(1, trades.size());
        Trade t = trades.getFirst();
        assertEquals("STOP_LOSS", t.exitReason());
        assertEquals(95.0, t.exitPrice(), 0.001);
    }

    @Test
    @DisplayName("Gap-through-stop uses worst-case fill (min(open, low))")
    void gapThroughStopWorstCaseFill() {
        BacktestEngine engine = new BacktestEngine();

        List<Candlestick> daily = List.of(
                candle(BASE_TS, 100, 101, 99, 100),
                candle(BASE_TS + DAY, 100, 101, 99, 100),
                candle(BASE_TS + 2 * DAY, 100, 101, 99, 100)
        );

        Map<Long, Double> rsi = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 50.0);
        IndicatorContext ctx = new IndicatorContext(rsi, null, 0, null, null);

        ExitRule exitRule = new ExitRule(5.0, null, null, null);
        EntryRule entryRule = new EntryRule("NEXT_BAR_OPEN", null, null, null, null);

        ExecutionConfig cfg = new ExecutionConfig(ExecutionMode.HYBRID_INTRADAY, "5m", 0.0, 0.0, 0.0, true);

        // stop at 95, bar gaps open at 90 with low 85 => fill at open=90.
        List<IntradayCandlestick> intraday = List.of(
                iCandle(BASE_TS + DAY + 60, 90, 92, 85, 88)
        );

        List<Trade> trades = engine.runForSymbol(
                "TEST.ST",
                daily,
                BASE_TS,
                List.of(rsiEntry(30, "below")),
                exitRule,
                null,
                ctx,
                entryRule,
                cfg,
                intraday
        );

        assertEquals(1, trades.size());
        Trade t = trades.getFirst();
        assertEquals("STOP_LOSS", t.exitReason());
        assertEquals(90.0, t.exitPrice(), 0.001);
    }

    @Test
    @DisplayName("Trailing stop high-water-mark updates on intraday closes (close-based)")
    void trailingUpdatesOnIntradayCloses() {
        BacktestEngine engine = new BacktestEngine();

        List<Candlestick> daily = List.of(
                candle(BASE_TS, 100, 101, 99, 100),
                candle(BASE_TS + DAY, 100, 101, 99, 100),
                candle(BASE_TS + 2 * DAY, 100, 101, 99, 100)
        );

        Map<Long, Double> rsi = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 50.0);
        IndicatorContext ctx = new IndicatorContext(rsi, null, 0, null, null);

        // trailing stopLossPercent=10% and trailingStopLoss=true
        ExitRule exitRule = new ExitRule(10.0, null, null, true);
        EntryRule entryRule = new EntryRule("NEXT_BAR_OPEN", null, null, null, null);
        ExecutionConfig cfg = new ExecutionConfig(ExecutionMode.HYBRID_INTRADAY, "5m", 0.0, 0.0, 0.0, true);

        // Entry day1 open=100.
        // Intraday bar 1 closes at 120 => HWM=120 => stop=108.
        // Intraday bar 2 low breaches 108 => exit at 108.
        List<IntradayCandlestick> intraday = List.of(
                iCandle(BASE_TS + DAY + 60, 100, 130, 100, 120),
                iCandle(BASE_TS + DAY + 120, 120, 121, 107, 110)
        );

        List<Trade> trades = engine.runForSymbol(
                "TEST.ST",
                daily,
                BASE_TS,
                List.of(rsiEntry(30, "below")),
                exitRule,
                null,
                ctx,
                entryRule,
                cfg,
                intraday
        );

        assertEquals(1, trades.size());
        Trade t = trades.getFirst();
        assertEquals("TRAILING_STOP_LOSS", t.exitReason());
        assertEquals(120.0 * 0.90, t.exitPrice(), 0.001);
    }

    @Test
    @DisplayName("HYBRID_INTRADAY falls back to daily-only when intraday data is missing")
    void fallsBackToDailyWhenIntradayMissing() {
        BacktestEngine engine = new BacktestEngine();

        // Entry on day1 open=100. Stop=95.
        // Day1 low hits 94 => daily stop exit at 95.
        List<Candlestick> daily = List.of(
                candle(BASE_TS, 100, 101, 99, 100),
                candle(BASE_TS + DAY, 100, 101, 94, 100),
                candle(BASE_TS + 2 * DAY, 100, 101, 99, 100)
        );

        Map<Long, Double> rsi = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 50.0);
        IndicatorContext ctx = new IndicatorContext(rsi, null, 0, null, null);

        ExitRule exitRule = new ExitRule(5.0, null, null, null);
        EntryRule entryRule = new EntryRule("NEXT_BAR_OPEN", null, null, null, null);
        ExecutionConfig cfg = new ExecutionConfig(ExecutionMode.HYBRID_INTRADAY, "5m", 0.0, 0.0, 0.0, true);

        List<Trade> trades = engine.runForSymbol(
                "TEST.ST",
                daily,
                BASE_TS,
                List.of(rsiEntry(30, "below")),
                exitRule,
                null,
                ctx,
                entryRule,
                cfg,
                List.of()
        );

        assertEquals(1, trades.size());
        Trade t = trades.getFirst();
        assertEquals("STOP_LOSS", t.exitReason());
        assertEquals(95.0, t.exitPrice(), 0.001);
    }
}
