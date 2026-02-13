package com.joakimcolloz.stocker.analysis.backtest;

import com.joakimcolloz.stocker.analysis.backtest.dto.EntryRule;
import com.joakimcolloz.stocker.analysis.backtest.dto.ExitRule;
import com.joakimcolloz.stocker.analysis.backtest.dto.Trade;
import com.joakimcolloz.stocker.analysis.backtest.engine.BacktestEngine;
import com.joakimcolloz.stocker.analysis.backtest.engine.IndicatorContext;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterRequest;
import com.joakimcolloz.stocker.analysis.dto.filter.HighVolumeFilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.MacdFilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.PatternFilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.RsiFilterParams;
import com.joakimcolloz.stocker.analysis.indicator.MacdCalculator.MacdValues;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BacktestEngine Unit Tests")
class BacktestEngineTest {

    private static final long DAY = 86_400L;
    private static final long BASE_TS = 1_704_067_200L; // 2024-01-01 00:00:00 UTC
    private static final EntryRule NEXT_BAR_OPEN = new EntryRule("NEXT_BAR_OPEN", null, null, null);

    private BacktestEngine engine;

    @BeforeEach
    void setUp() {
        engine = new BacktestEngine();
    }

    private Candlestick candle(long timestamp, double open, double high, double low, double close, long volume) {
        return new Candlestick(open, high, low, close, volume, timestamp, "TEST.ST");
    }

    private FilterRequest rsiFilter(double threshold, String condition) {
        FilterRequest filter = new FilterRequest();
        filter.setType("RSI");
        filter.setParams(new RsiFilterParams(threshold, condition));
        return filter;
    }

    private FilterRequest macdFilter(String condition) {
        FilterRequest filter = new FilterRequest();
        filter.setType("MACD");
        filter.setParams(new MacdFilterParams(condition, null, null, null));
        return filter;
    }

    private FilterRequest highVolumeFilter(int percentageAbove) {
        FilterRequest filter = new FilterRequest();
        filter.setType("HIGH_VOLUME");
        filter.setParams(new HighVolumeFilterParams(percentageAbove));
        return filter;
    }

    private FilterRequest patternFilter() {
        FilterRequest filter = new FilterRequest();
        filter.setType("PATTERN");
        filter.setParams(new PatternFilterParams("HAMMER"));
        return filter;
    }

    @Nested
    @DisplayName("RSI Entry + Take Profit Exit (NEXT_BAR_OPEN)")
    class RsiTakeProfit {

        @Test
        @DisplayName("Should enter on next bar open after RSI signal and exit on take-profit")
        void entryOnRsiBelowExitOnTakeProfit() {
            // Bar 0: RSI=25 (below 30) → signal fires, pending order created
            // Bar 1: entry at open=101, high=120 → take-profit at 101*1.15=116.15, high=120 triggers it
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 101, 120, 99, 118, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 45.0);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);

            ExitRule exitRule = new ExitRule(null, 15.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx, NEXT_BAR_OPEN);

            assertEquals(1, trades.size());
            Trade trade = trades.getFirst();
            assertEquals("TEST.ST", trade.symbol());
            assertEquals(BASE_TS + DAY, trade.entryTimestamp());
            assertEquals(101.0, trade.entryPrice());
            assertEquals("TAKE_PROFIT", trade.exitReason());
            assertEquals(101.0 * 1.15, trade.exitPrice(), 0.001);
            assertEquals(15.0, trade.returnPercent(), 0.001);
        }
    }

    @Nested
    @DisplayName("MACD Entry + Stop Loss Exit (NEXT_BAR_OPEN)")
    class MacdStopLoss {

        @Test
        @DisplayName("Should enter on next bar open after MACD crossover_above signal and exit on stop-loss")
        void entryOnMacdCrossoverAboveExitOnStopLoss() {
            // Bar 0 (lookback): prev bar for crossover detection
            // Bar 1: MACD cross above → signal fires, pending order created
            // Bar 2: entry at open=198, low=185 → stop-loss at 198*0.95=188.1, low=185 triggers it
            List<Candlestick> candles = List.of(
                    candle(BASE_TS - DAY, 190, 195, 188, 192, 1000),
                    candle(BASE_TS, 195, 205, 193, 200, 1000),
                    candle(BASE_TS + DAY, 198, 202, 185, 188, 1000)
            );

            Map<Long, MacdValues> macdValues = Map.of(
                    BASE_TS - DAY, new MacdValues(-1.0, 0.5, -1.5),
                    BASE_TS, new MacdValues(1.0, 0.5, 0.5),
                    BASE_TS + DAY, new MacdValues(0.8, 0.9, -0.1)
            );

            IndicatorContext ctx = new IndicatorContext(null, macdValues, 0, null);
            ExitRule exitRule = new ExitRule(5.0, null, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(macdFilter("crossover_above")), exitRule, ctx, NEXT_BAR_OPEN);

            assertEquals(1, trades.size());
            Trade trade = trades.getFirst();
            assertEquals(198.0, trade.entryPrice());
            assertEquals("STOP_LOSS", trade.exitReason());
            assertEquals(198.0 * 0.95, trade.exitPrice(), 0.001);
        }
    }

    @Nested
    @DisplayName("Combined Entry Conditions (NEXT_BAR_OPEN)")
    class CombinedEntryConditions {

        @Test
        @DisplayName("Should only enter when RSI + MACD + HIGH_VOLUME all satisfied")
        void combinedRsiMacdHighVolume() {
            // Bar 0: RSI=25, MACD above_zero, volume=2000 → all met, signal fires
            // Bar 1: entry at open=101 → no exit triggered
            // Bar 2: high=115 → take-profit at 101*1.10=111.1, high=115 triggers it
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 2000),
                    candle(BASE_TS + DAY, 101, 104, 99, 103, 800),
                    candle(BASE_TS + 2 * DAY, 103, 115, 101, 112, 900)
            );

            Map<Long, Double> rsiValues = Map.of(
                    BASE_TS, 25.0,
                    BASE_TS + DAY, 40.0,
                    BASE_TS + 2 * DAY, 55.0
            );
            Map<Long, MacdValues> macdValues = Map.of(
                    BASE_TS, new MacdValues(1.0, 0.5, 0.5),
                    BASE_TS + DAY, new MacdValues(1.2, 0.8, 0.4),
                    BASE_TS + 2 * DAY, new MacdValues(1.5, 1.0, 0.5)
            );

            IndicatorContext ctx = new IndicatorContext(rsiValues, macdValues, 1000.0, null);
            ExitRule exitRule = new ExitRule(null, 10.0, null);

            List<FilterRequest> conditions = List.of(
                    rsiFilter(30, "below"),
                    macdFilter("above_zero"),
                    highVolumeFilter(50)
            );

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, conditions, exitRule, ctx, NEXT_BAR_OPEN);

            assertEquals(1, trades.size());
            Trade trade = trades.getFirst();
            assertEquals(101.0, trade.entryPrice());
            assertEquals("TAKE_PROFIT", trade.exitReason());
            assertEquals(101.0 * 1.10, trade.exitPrice(), 0.001);
        }

        @Test
        @DisplayName("Should not enter when one condition not met")
        void noEntryWhenOneConditionFails() {
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 2000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0);
            Map<Long, MacdValues> macdValues = Map.of(
                    BASE_TS, new MacdValues(-1.0, 0.5, -1.5)
            );

            IndicatorContext ctx = new IndicatorContext(rsiValues, macdValues, 1000.0, null);
            ExitRule exitRule = new ExitRule(5.0, 10.0, null);

            List<FilterRequest> conditions = List.of(
                    rsiFilter(30, "below"),
                    macdFilter("above_zero"),
                    highVolumeFilter(50)
            );

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, conditions, exitRule, ctx, NEXT_BAR_OPEN);

            assertTrue(trades.isEmpty());
        }
    }

    @Nested
    @DisplayName("Max Holding Days Exit (NEXT_BAR_OPEN)")
    class MaxHoldingDays {

        @Test
        @DisplayName("Should force exit after max holding days")
        void exitAfterMaxHoldingDays() {
            List<Candlestick> candles = new ArrayList<>();
            // Day 0: signal fires (RSI=25)
            candles.add(candle(BASE_TS, 98, 102, 95, 100, 1000));
            // Day 1: entry at open=99, then days 1-3 no stop/TP triggered
            candles.add(candle(BASE_TS + DAY, 99, 104, 96, 101, 1000));
            candles.add(candle(BASE_TS + 2 * DAY, 100, 104, 96, 102, 1000));
            candles.add(candle(BASE_TS + 3 * DAY, 101, 104, 96, 103, 1000));
            // Day 4: holding = (4*DAY) / DAY = 4 days >= 3 → exit
            candles.add(candle(BASE_TS + 4 * DAY, 102, 105, 97, 104, 1000));

            Map<Long, Double> rsiValues = new HashMap<>();
            rsiValues.put(BASE_TS, 25.0);
            rsiValues.put(BASE_TS + DAY, 45.0);
            rsiValues.put(BASE_TS + 2 * DAY, 50.0);
            rsiValues.put(BASE_TS + 3 * DAY, 55.0);
            rsiValues.put(BASE_TS + 4 * DAY, 60.0);

            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(null, null, 3);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx, NEXT_BAR_OPEN);

            assertEquals(1, trades.size());
            Trade trade = trades.getFirst();
            assertEquals(99.0, trade.entryPrice());
            assertEquals("MAX_HOLDING_DAYS", trade.exitReason());
            assertEquals(BASE_TS + 4 * DAY, trade.exitTimestamp());
            assertEquals(104.0, trade.exitPrice());
        }
    }

    @Nested
    @DisplayName("No Trades")
    class NoTrades {

        @Test
        @DisplayName("Should produce no trades when conditions never met")
        void noTradesWhenConditionsNeverMet() {
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 99, 103, 96, 101, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 55.0, BASE_TS + DAY, 60.0);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(5.0, 10.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx, NEXT_BAR_OPEN);

            assertTrue(trades.isEmpty());
        }

        @Test
        @DisplayName("Should produce no trades for empty candle list")
        void noTradesForEmptyCandles() {
            IndicatorContext ctx = new IndicatorContext(null, null, 0, null);
            ExitRule exitRule = new ExitRule(5.0, 10.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", List.of(), BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx, NEXT_BAR_OPEN);

            assertTrue(trades.isEmpty());
        }
    }

    @Nested
    @DisplayName("Multiple Trades Per Symbol (NEXT_BAR_OPEN)")
    class MultipleTrades {

        @Test
        @DisplayName("Should enter, exit, and re-enter")
        void enterExitReenter() {
            // Bar 0: RSI=25 → signal, pending order
            // Bar 1: entry at open=101, high=116 → take-profit at 101*1.15=116.15, high=116 < 116.15 so no TP
            //         but close=114, and we need TP to trigger. Let's adjust: TP at 10%.
            //         101*1.10=111.1 → high=116 triggers TP at 111.1. Exit. RSI=45 no signal.
            // Bar 2: RSI=28 → signal, pending order
            // Bar 3: entry at open=119, low=110 → stop-loss at 119*0.95=113.05, low=110 triggers it
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 101, 116, 99, 114, 1000),
                    candle(BASE_TS + 2 * DAY, 115, 122, 113, 120, 1000),
                    candle(BASE_TS + 3 * DAY, 119, 121, 110, 112, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(
                    BASE_TS, 25.0,
                    BASE_TS + DAY, 45.0,
                    BASE_TS + 2 * DAY, 28.0,
                    BASE_TS + 3 * DAY, 35.0
            );

            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(5.0, 10.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx, NEXT_BAR_OPEN);

            assertEquals(2, trades.size());

            Trade first = trades.get(0);
            assertEquals(101.0, first.entryPrice());
            assertEquals("TAKE_PROFIT", first.exitReason());
            assertEquals(101.0 * 1.10, first.exitPrice(), 0.001);

            Trade second = trades.get(1);
            assertEquals(119.0, second.entryPrice());
            assertEquals("STOP_LOSS", second.exitReason());
            assertEquals(119.0 * 0.95, second.exitPrice(), 0.001);
        }
    }

    @Nested
    @DisplayName("End of Data Exit (NEXT_BAR_OPEN)")
    class EndOfData {

        @Test
        @DisplayName("Should close open position at end of data")
        void endOfDataForceExit() {
            // Bar 0: RSI=25 → signal
            // Bar 1: entry at open=101, no exit triggered
            // End of data → force exit at bar 1 close=103
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 101, 104, 97, 103, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 40.0);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(10.0, 20.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx, NEXT_BAR_OPEN);

            assertEquals(1, trades.size());
            Trade trade = trades.getFirst();
            assertEquals(101.0, trade.entryPrice());
            assertEquals("END_OF_DATA", trade.exitReason());
            assertEquals(103.0, trade.exitPrice());
        }
    }

    @Nested
    @DisplayName("Stop-Loss Uses Bar Low, Take-Profit Uses Bar High (NEXT_BAR_OPEN)")
    class IntradayPriceChecks {

        @Test
        @DisplayName("Stop-loss triggers on bar low even when close is higher")
        void stopLossUsesBarLow() {
            // Bar 0: RSI=25 → signal
            // Bar 1: entry at open=99. Stop at 99*0.95=94.05.
            // Bar 2: low=93 triggers stop at 94.05
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 99, 101, 96, 98, 1000),
                    candle(BASE_TS + 2 * DAY, 97, 100, 93, 98, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 40.0, BASE_TS + 2 * DAY, 42.0);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(5.0, null, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx, NEXT_BAR_OPEN);

            assertEquals(1, trades.size());
            assertEquals("STOP_LOSS", trades.getFirst().exitReason());
            assertEquals(99.0 * 0.95, trades.getFirst().exitPrice(), 0.001);
        }

        @Test
        @DisplayName("Take-profit triggers on bar high even when close is lower")
        void takeProfitUsesBarHigh() {
            // Bar 0: RSI=25 → signal
            // Bar 1: entry at open=101. TP at 101*1.10=111.1.
            // Bar 2: high=112 triggers TP at 111.1
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 101, 104, 99, 103, 1000),
                    candle(BASE_TS + 2 * DAY, 103, 112, 99, 105, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 50.0, BASE_TS + 2 * DAY, 55.0);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(null, 10.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx, NEXT_BAR_OPEN);

            assertEquals(1, trades.size());
            assertEquals("TAKE_PROFIT", trades.getFirst().exitReason());
            assertEquals(101.0 * 1.10, trades.getFirst().exitPrice(), 0.001);
        }
    }

    @Nested
    @DisplayName("Trading Window (NEXT_BAR_OPEN)")
    class TradingWindow {

        @Test
        @DisplayName("Should not enter before trading start timestamp")
        void noEntryBeforeTradingStart() {
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 99, 103, 96, 101, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 55.0);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(5.0, 15.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS + DAY, List.of(rsiFilter(30, "below")), exitRule, ctx, NEXT_BAR_OPEN);

            assertTrue(trades.isEmpty());
        }
    }

    @Nested
    @DisplayName("PATTERN Entry (NEXT_BAR_OPEN)")
    class PatternEntry {

        @Test
        @DisplayName("Should enter on next bar open after PATTERN match and exit on take-profit")
        void entryOnPatternMatchExitOnTakeProfit() {
            // Bar 0: pattern detected → signal
            // Bar 1: entry at open=101. TP at 101*1.10=111.1. high=112 triggers it.
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 101, 112, 99, 108, 1000)
            );

            Set<Long> patternTimestamps = Set.of(BASE_TS);
            IndicatorContext ctx = new IndicatorContext(null, null, 0, patternTimestamps);
            ExitRule exitRule = new ExitRule(null, 10.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(patternFilter()), exitRule, ctx, NEXT_BAR_OPEN);

            assertEquals(1, trades.size());
            Trade trade = trades.getFirst();
            assertEquals(BASE_TS + DAY, trade.entryTimestamp());
            assertEquals(101.0, trade.entryPrice());
            assertEquals("TAKE_PROFIT", trade.exitReason());
            assertEquals(101.0 * 1.10, trade.exitPrice(), 0.001);
        }

        @Test
        @DisplayName("Should not enter when pattern timestamp not in set")
        void noEntryWhenPatternNotMatched() {
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 99, 103, 96, 101, 1000)
            );

            Set<Long> patternTimestamps = Set.of(BASE_TS + 5 * DAY);
            IndicatorContext ctx = new IndicatorContext(null, null, 0, patternTimestamps);
            ExitRule exitRule = new ExitRule(5.0, 10.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(patternFilter()), exitRule, ctx, NEXT_BAR_OPEN);

            assertTrue(trades.isEmpty());
        }

        @Test
        @DisplayName("Should enter only when both PATTERN and RSI conditions are met")
        void combinedPatternAndRsi() {
            // Bar 0: pattern ✓, RSI=25 ✓ → signal
            // Bar 1: entry at open=101, high=112 → TP at 101*1.10=111.1 triggers
            // Bar 2: pattern ✗, RSI=28 ✓ → no entry (pattern not matched)
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 101, 112, 99, 108, 1000),
                    candle(BASE_TS + 2 * DAY, 107, 110, 105, 109, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(
                    BASE_TS, 25.0,
                    BASE_TS + DAY, 55.0,
                    BASE_TS + 2 * DAY, 28.0
            );
            Set<Long> patternTimestamps = Set.of(BASE_TS, BASE_TS + DAY);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, patternTimestamps);
            ExitRule exitRule = new ExitRule(5.0, 10.0, null);

            List<FilterRequest> conditions = List.of(patternFilter(), rsiFilter(30, "below"));

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, conditions, exitRule, ctx, NEXT_BAR_OPEN);

            assertEquals(1, trades.size());
            Trade trade = trades.getFirst();
            assertEquals(101.0, trade.entryPrice());
            assertEquals("TAKE_PROFIT", trade.exitReason());
            assertEquals(101.0 * 1.10, trade.exitPrice(), 0.001);
        }
    }

    @Nested
    @DisplayName("NextBarOpenEntry")
    class NextBarOpenEntry {

        @Test
        @DisplayName("Signal on bar 0, entry at bar 1's open, exit on take-profit")
        void signalOnBar0EntryAtBar1Open() {
            // Bar 0: RSI=25 → signal
            // Bar 1: entry at open=105. TP at 105*1.10=115.5. high=116 → triggers TP.
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 105, 116, 104, 112, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 50.0);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(null, 10.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx, NEXT_BAR_OPEN);

            assertEquals(1, trades.size());
            Trade trade = trades.getFirst();
            assertEquals(BASE_TS + DAY, trade.entryTimestamp());
            assertEquals(105.0, trade.entryPrice());
            assertEquals("TAKE_PROFIT", trade.exitReason());
        }

        @Test
        @DisplayName("Signal on last bar → no entry (no next bar to fill)")
        void signalOnLastBarNoEntry() {
            // Only one bar, signal fires but no next bar to fill
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(5.0, 10.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx, NEXT_BAR_OPEN);

            // Pending order dropped at end of data
            assertTrue(trades.isEmpty());
        }

        @Test
        @DisplayName("Signal, entry, exit, re-entry cycle")
        void signalEntryExitReentry() {
            // Bar 0: RSI=25 → signal
            // Bar 1: entry at open=101. TP at 101*1.05=106.05. high=107 → TP triggers. RSI=28 → new signal.
            // Bar 2: entry at open=108. Stop at 108*0.95=102.6. low=102 → stop triggers.
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 101, 107, 99, 105, 1000),
                    candle(BASE_TS + 2 * DAY, 108, 110, 102, 104, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(
                    BASE_TS, 25.0,
                    BASE_TS + DAY, 28.0,
                    BASE_TS + 2 * DAY, 50.0
            );

            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(5.0, 5.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx, NEXT_BAR_OPEN);

            assertEquals(2, trades.size());

            Trade first = trades.get(0);
            assertEquals(101.0, first.entryPrice());
            assertEquals("TAKE_PROFIT", first.exitReason());

            Trade second = trades.get(1);
            assertEquals(108.0, second.entryPrice());
            assertEquals("STOP_LOSS", second.exitReason());
        }
    }

    @Nested
    @DisplayName("LimitOrderEntry")
    class LimitOrderEntry {

        @Test
        @DisplayName("Limit order fills when low penetrates limit price")
        void limitOrderFillsOnPenetration() {
            // Bar 0: RSI=25 → signal. close=100. limit = 100*(1-2/100) = 98
            // Bar 1: low=97 <= 98 → fill at 98
            // Bar 2: high=110 → TP at 98*1.10=107.8 → triggers
            EntryRule limitOrder = new EntryRule("LIMIT_ORDER", 2.0, 0.0, 3);

            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 99, 101, 97, 99, 1000),
                    candle(BASE_TS + 2 * DAY, 100, 110, 98, 108, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 40.0, BASE_TS + 2 * DAY, 55.0);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(5.0, 10.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx, limitOrder);

            assertEquals(1, trades.size());
            Trade trade = trades.getFirst();
            assertEquals(98.0, trade.entryPrice());
            assertEquals(BASE_TS + DAY, trade.entryTimestamp());
            assertEquals("TAKE_PROFIT", trade.exitReason());
        }

        @Test
        @DisplayName("Limit order expires after maxBarsToFill → no trade")
        void limitOrderExpires() {
            // Bar 0: RSI=25 → signal. close=100. limit=98. maxBarsToFill=1
            // Bar 1: low=99 > 98 → not filled. barsRemaining=0 → expired
            // Bar 2: RSI=50 → no new signal
            EntryRule limitOrder = new EntryRule("LIMIT_ORDER", 2.0, 0.0, 1);

            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 99, 101, 99, 100, 1000),
                    candle(BASE_TS + 2 * DAY, 100, 102, 98, 101, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 50.0, BASE_TS + 2 * DAY, 50.0);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(5.0, 10.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx, limitOrder);

            assertTrue(trades.isEmpty());
        }

        @Test
        @DisplayName("Limit with 0% offset and 0% penetration fills if low <= signal close")
        void limitZeroOffsetZeroPenetration() {
            // Bar 0: RSI=25 → signal. close=100. limit=100 (0% offset). fillThreshold=100 (0% pen).
            // Bar 1: low=100 <= 100 → fill at 100
            EntryRule limitOrder = new EntryRule("LIMIT_ORDER", 0.0, 0.0, 1);

            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 101, 104, 100, 103, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 50.0);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(10.0, 20.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx, limitOrder);

            assertEquals(1, trades.size());
            assertEquals(100.0, trades.getFirst().entryPrice());
        }

        @Test
        @DisplayName("Combined limit order with pattern and RSI conditions")
        void combinedLimitOrderPatternRsi() {
            // Bar 0: pattern ✓, RSI=25 ✓ → signal. close=100. limit=99 (1% offset).
            // Bar 1: low=98 <= 99 → fill at 99. TP at 99*1.10=108.9. high=110 → triggers.
            EntryRule limitOrder = new EntryRule("LIMIT_ORDER", 1.0, 0.0, 2);

            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 101, 110, 98, 108, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 50.0);
            Set<Long> patternTimestamps = Set.of(BASE_TS);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, patternTimestamps);
            ExitRule exitRule = new ExitRule(5.0, 10.0, null);

            List<FilterRequest> conditions = List.of(patternFilter(), rsiFilter(30, "below"));

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, conditions, exitRule, ctx, limitOrder);

            assertEquals(1, trades.size());
            Trade trade = trades.getFirst();
            assertEquals(99.0, trade.entryPrice());
            assertEquals("TAKE_PROFIT", trade.exitReason());
        }

        @Test
        @DisplayName("Pending order blocks new signals")
        void pendingOrderBlocksNewSignals() {
            // Bar 0: RSI=25 → signal. close=100. limit=95 (5% offset). maxBarsToFill=2
            // Bar 1: low=97 > 95. RSI=28 but pending order blocks new signal. barsRemaining=1
            // Bar 2: low=94 <= 95 → fill at 95. RSI=29 irrelevant.
            EntryRule limitOrder = new EntryRule("LIMIT_ORDER", 5.0, 0.0, 2);

            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 99, 101, 97, 98, 1000),
                    candle(BASE_TS + 2 * DAY, 97, 100, 94, 96, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 28.0, BASE_TS + 2 * DAY, 29.0);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(10.0, 20.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx, limitOrder);

            // Only one trade from the original signal, not from bar 1's RSI
            assertEquals(1, trades.size());
            assertEquals(95.0, trades.getFirst().entryPrice());
        }

        @Test
        @DisplayName("Limit order with penetration percent requires deeper penetration")
        void limitOrderWithPenetration() {
            // Bar 0: RSI=25 → signal. close=100. limit=98 (2% offset). fillThreshold=98*(1-1/100)=97.02
            // Bar 1: low=97.5 > 97.02 → not filled
            // Bar 2: low=96 <= 97.02 → fill at 98
            EntryRule limitOrder = new EntryRule("LIMIT_ORDER", 2.0, 1.0, 3);

            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 99, 101, 97.5, 99, 1000),
                    candle(BASE_TS + 2 * DAY, 98, 102, 96, 100, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 40.0, BASE_TS + 2 * DAY, 50.0);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(10.0, 20.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx, limitOrder);

            assertEquals(1, trades.size());
            assertEquals(98.0, trades.getFirst().entryPrice());
            assertEquals(BASE_TS + 2 * DAY, trades.getFirst().entryTimestamp());
        }
    }
}
