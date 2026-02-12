package com.joakimcolloz.stocker.analysis.backtest;

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
    @DisplayName("RSI Entry + Take Profit Exit")
    class RsiTakeProfit {

        @Test
        @DisplayName("Should enter on RSI below threshold and exit on take-profit")
        void entryOnRsiBelowExitOnTakeProfit() {
            // Bar 0: RSI=25 (below 30) → entry at close=100
            // Bar 1: high=120 → take-profit at 115 (15% above 100)
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 101, 120, 99, 118, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 45.0);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);

            ExitRule exitRule = new ExitRule(null, 15.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx);

            assertEquals(1, trades.size());
            Trade trade = trades.getFirst();
            assertEquals("TEST.ST", trade.symbol());
            assertEquals(BASE_TS, trade.entryTimestamp());
            assertEquals(100.0, trade.entryPrice());
            assertEquals("TAKE_PROFIT", trade.exitReason());
            assertEquals(115.0, trade.exitPrice(), 0.001);
            assertEquals(15.0, trade.returnPercent(), 0.001);
        }
    }

    @Nested
    @DisplayName("MACD Entry + Stop Loss Exit")
    class MacdStopLoss {

        @Test
        @DisplayName("Should enter on MACD crossover_above and exit on stop-loss")
        void entryOnMacdCrossoverAboveExitOnStopLoss() {
            // Bar 0: MACD cross above → entry at close=200
            // Bar 1: low=185 → stop-loss at 190 (5% below 200)
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 195, 205, 193, 200, 1000),
                    candle(BASE_TS + DAY, 198, 202, 185, 188, 1000)
            );

            Map<Long, MacdValues> macdValues = Map.of(
                    BASE_TS - DAY, new MacdValues(-1.0, 0.5, -1.5),  // prev: MACD < signal
                    BASE_TS, new MacdValues(1.0, 0.5, 0.5),           // curr: MACD > signal → crossover
                    BASE_TS + DAY, new MacdValues(0.8, 0.9, -0.1)
            );

            // Need a previous bar for crossover detection
            List<Candlestick> candlesWithPrev = List.of(
                    candle(BASE_TS - DAY, 190, 195, 188, 192, 1000),
                    candle(BASE_TS, 195, 205, 193, 200, 1000),
                    candle(BASE_TS + DAY, 198, 202, 185, 188, 1000)
            );

            IndicatorContext ctx = new IndicatorContext(null, macdValues, 0, null);
            ExitRule exitRule = new ExitRule(5.0, null, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candlesWithPrev, BASE_TS, List.of(macdFilter("crossover_above")), exitRule, ctx);

            assertEquals(1, trades.size());
            Trade trade = trades.getFirst();
            assertEquals("STOP_LOSS", trade.exitReason());
            assertEquals(190.0, trade.exitPrice(), 0.001);
            assertEquals(-5.0, trade.returnPercent(), 0.001);
        }
    }

    @Nested
    @DisplayName("Combined Entry Conditions")
    class CombinedEntryConditions {

        @Test
        @DisplayName("Should only enter when RSI + MACD + HIGH_VOLUME all satisfied")
        void combinedRsiMacdHighVolume() {
            // Bar 0: RSI=25, MACD above_zero, volume=2000 (avg=1000, need 50% above → 1500)
            // All conditions met → entry at close=100
            // Bar 1: Nothing exits yet
            // Bar 2: Take profit at 110 (10% above 100)
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
                    "TEST.ST", candles, BASE_TS, conditions, exitRule, ctx);

            assertEquals(1, trades.size());
            Trade trade = trades.getFirst();
            assertEquals(100.0, trade.entryPrice());
            assertEquals("TAKE_PROFIT", trade.exitReason());
            assertEquals(110.0, trade.exitPrice(), 0.001);
        }

        @Test
        @DisplayName("Should not enter when one condition not met")
        void noEntryWhenOneConditionFails() {
            // RSI=25 (below 30 ✓), MACD below zero (above_zero ✗), HIGH_VOLUME ✓
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 2000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0);
            Map<Long, MacdValues> macdValues = Map.of(
                    BASE_TS, new MacdValues(-1.0, 0.5, -1.5)  // MACD below zero
            );

            IndicatorContext ctx = new IndicatorContext(rsiValues, macdValues, 1000.0, null);
            ExitRule exitRule = new ExitRule(5.0, 10.0, null);

            List<FilterRequest> conditions = List.of(
                    rsiFilter(30, "below"),
                    macdFilter("above_zero"),
                    highVolumeFilter(50)
            );

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, conditions, exitRule, ctx);

            assertTrue(trades.isEmpty());
        }
    }

    @Nested
    @DisplayName("Max Holding Days Exit")
    class MaxHoldingDays {

        @Test
        @DisplayName("Should force exit after max holding days")
        void exitAfterMaxHoldingDays() {
            List<Candlestick> candles = new ArrayList<>();
            // Day 0: entry
            candles.add(candle(BASE_TS, 98, 102, 95, 100, 1000));
            // Days 1-3: price doesn't trigger stop or take-profit
            for (int i = 1; i <= 3; i++) {
                candles.add(candle(BASE_TS + i * DAY, 99, 104, 96, 101 + i, 1000));
            }

            Map<Long, Double> rsiValues = new HashMap<>();
            rsiValues.put(BASE_TS, 25.0);       // Entry condition met on day 0
            rsiValues.put(BASE_TS + DAY, 45.0);  // Above threshold — no re-entry
            rsiValues.put(BASE_TS + 2 * DAY, 50.0);
            rsiValues.put(BASE_TS + 3 * DAY, 55.0);

            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(null, null, 3);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx);

            assertEquals(1, trades.size());
            Trade trade = trades.getFirst();
            assertEquals("MAX_HOLDING_DAYS", trade.exitReason());
            assertEquals(BASE_TS + 3 * DAY, trade.exitTimestamp());
            assertEquals(104.0, trade.exitPrice()); // close of day 3
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

            // RSI always above 50 → "below 30" never met
            Map<Long, Double> rsiValues = Map.of(BASE_TS, 55.0, BASE_TS + DAY, 60.0);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(5.0, 10.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx);

            assertTrue(trades.isEmpty());
        }

        @Test
        @DisplayName("Should produce no trades for empty candle list")
        void noTradesForEmptyCandles() {
            IndicatorContext ctx = new IndicatorContext(null, null, 0, null);
            ExitRule exitRule = new ExitRule(5.0, 10.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", List.of(), BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx);

            assertTrue(trades.isEmpty());
        }
    }

    @Nested
    @DisplayName("Multiple Trades Per Symbol")
    class MultipleTrades {

        @Test
        @DisplayName("Should enter, exit, and re-enter")
        void enterExitReenter() {
            // Bar 0: RSI=25 → entry at 100
            // Bar 1: high=116 → take-profit at 115 (15%) → exit
            // Bar 2: RSI=28 → re-entry at 120
            // Bar 3: low=110 → stop-loss at 114 (5% below 120) → exit
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
            ExitRule exitRule = new ExitRule(5.0, 15.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx);

            assertEquals(2, trades.size());

            Trade first = trades.get(0);
            assertEquals(100.0, first.entryPrice());
            assertEquals("TAKE_PROFIT", first.exitReason());
            assertEquals(115.0, first.exitPrice(), 0.001);

            Trade second = trades.get(1);
            assertEquals(120.0, second.entryPrice());
            assertEquals("STOP_LOSS", second.exitReason());
            assertEquals(114.0, second.exitPrice(), 0.001);
        }
    }

    @Nested
    @DisplayName("End of Data Exit")
    class EndOfData {

        @Test
        @DisplayName("Should close open position at end of data")
        void endOfDataForceExit() {
            // Bar 0: RSI=25 → entry at 100
            // Bar 1: no exit triggered → still in position
            // End of data → force exit at bar 1 close
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 101, 104, 97, 103, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 40.0);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(10.0, 20.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx);

            assertEquals(1, trades.size());
            Trade trade = trades.getFirst();
            assertEquals("END_OF_DATA", trade.exitReason());
            assertEquals(103.0, trade.exitPrice());
            assertEquals(3.0, trade.returnPercent(), 0.001);
        }
    }

    @Nested
    @DisplayName("Stop-Loss Uses Bar Low, Take-Profit Uses Bar High")
    class IntradayPriceChecks {

        @Test
        @DisplayName("Stop-loss triggers on bar low even when close is higher")
        void stopLossUsesBarLow() {
            // Entry at 100, stop-loss at 5% → triggers at 95
            // Bar low=94 (triggers stop) but close=98 (above stop)
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 99, 101, 94, 98, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 40.0);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(5.0, null, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx);

            assertEquals(1, trades.size());
            assertEquals("STOP_LOSS", trades.getFirst().exitReason());
            assertEquals(95.0, trades.getFirst().exitPrice(), 0.001);
        }

        @Test
        @DisplayName("Take-profit triggers on bar high even when close is lower")
        void takeProfitUsesBarHigh() {
            // Entry at 100, take-profit at 10% → triggers at 110
            // Bar high=112 (triggers TP) but close=105 (below TP)
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 101, 112, 99, 105, 1000)
            );

            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 50.0);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(null, 10.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(rsiFilter(30, "below")), exitRule, ctx);

            assertEquals(1, trades.size());
            assertEquals("TAKE_PROFIT", trades.getFirst().exitReason());
            assertEquals(110.0, trades.getFirst().exitPrice(), 0.001);
        }
    }

    @Nested
    @DisplayName("Trading Window")
    class TradingWindow {

        @Test
        @DisplayName("Should not enter before trading start timestamp")
        void noEntryBeforeTradingStart() {
            // Bar 0 is before trading window, bar 1 is inside
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),       // before trading start
                    candle(BASE_TS + DAY, 99, 103, 96, 101, 1000)  // after trading start
            );

            // RSI below 30 on bar 0 only
            Map<Long, Double> rsiValues = Map.of(BASE_TS, 25.0, BASE_TS + DAY, 55.0);
            IndicatorContext ctx = new IndicatorContext(rsiValues, null, 0, null);
            ExitRule exitRule = new ExitRule(5.0, 15.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS + DAY, List.of(rsiFilter(30, "below")), exitRule, ctx);

            // No entry on bar 0 (before window), no entry on bar 1 (RSI above 30)
            assertTrue(trades.isEmpty());
        }
    }

    @Nested
    @DisplayName("PATTERN Entry")
    class PatternEntry {

        @Test
        @DisplayName("Should enter on PATTERN match and exit on take-profit")
        void entryOnPatternMatchExitOnTakeProfit() {
            // Bar 0: pattern detected (timestamp in set) → entry at close=100
            // Bar 1: high=112 → take-profit at 110 (10% above 100)
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 101, 112, 99, 108, 1000)
            );

            Set<Long> patternTimestamps = Set.of(BASE_TS);
            IndicatorContext ctx = new IndicatorContext(null, null, 0, patternTimestamps);
            ExitRule exitRule = new ExitRule(null, 10.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(patternFilter()), exitRule, ctx);

            assertEquals(1, trades.size());
            Trade trade = trades.getFirst();
            assertEquals(BASE_TS, trade.entryTimestamp());
            assertEquals(100.0, trade.entryPrice());
            assertEquals("TAKE_PROFIT", trade.exitReason());
            assertEquals(110.0, trade.exitPrice(), 0.001);
        }

        @Test
        @DisplayName("Should not enter when pattern timestamp not in set")
        void noEntryWhenPatternNotMatched() {
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 98, 102, 95, 100, 1000),
                    candle(BASE_TS + DAY, 99, 103, 96, 101, 1000)
            );

            // Pattern detected on a different timestamp — not on any bar in the series
            Set<Long> patternTimestamps = Set.of(BASE_TS + 5 * DAY);
            IndicatorContext ctx = new IndicatorContext(null, null, 0, patternTimestamps);
            ExitRule exitRule = new ExitRule(5.0, 10.0, null);

            List<Trade> trades = engine.runForSymbol(
                    "TEST.ST", candles, BASE_TS, List.of(patternFilter()), exitRule, ctx);

            assertTrue(trades.isEmpty());
        }

        @Test
        @DisplayName("Should enter only when both PATTERN and RSI conditions are met")
        void combinedPatternAndRsi() {
            // Bar 0: pattern match ✓, RSI=25 (below 30) ✓ → entry at close=100
            // Bar 1: pattern match ✓, RSI=55 (above 30) ✗ → no entry (already in position anyway)
            // Bar 1: high=112 → take-profit at 110 (10% above 100) → exit
            // Bar 2: pattern match ✗, RSI=28 (below 30) ✓ → no entry (pattern not matched)
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
                    "TEST.ST", candles, BASE_TS, conditions, exitRule, ctx);

            // Only 1 trade: entry on bar 0, exit take-profit on bar 1
            // Bar 2 has RSI below 30 but no pattern match → no re-entry
            assertEquals(1, trades.size());
            Trade trade = trades.getFirst();
            assertEquals(100.0, trade.entryPrice());
            assertEquals("TAKE_PROFIT", trade.exitReason());
            assertEquals(110.0, trade.exitPrice(), 0.001);
        }
    }
}
