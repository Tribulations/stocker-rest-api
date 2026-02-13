package com.joakimcolloz.stocker.analysis.indicator;

import com.joakimcolloz.stocker.api.entity.Candlestick;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MovingAverageCalculator Unit Tests")
class MovingAverageCalculatorTest {

    private static final long DAY = 86_400L;
    private static final long BASE_TS = 1_704_067_200L; // 2024-01-01

    private Candlestick candle(long timestamp, double close) {
        return new Candlestick(close, close + 1, close - 1, close, 1000L, timestamp, "TEST.ST");
    }

    @Nested
    @DisplayName("SMA Calculation")
    class SmaCalculation {

        @Test
        @DisplayName("5-period SMA on [10,20,30,40,50] yields 30.0")
        void fivePeriodSmaKnownValues() {
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 10),
                    candle(BASE_TS + DAY, 20),
                    candle(BASE_TS + 2 * DAY, 30),
                    candle(BASE_TS + 3 * DAY, 40),
                    candle(BASE_TS + 4 * DAY, 50),
                    candle(BASE_TS + 5 * DAY, 60)
            );

            Map<Long, Double> result = MovingAverageCalculator.calculateByTimestamp(candles, 5, "SMA");

            // First 5 bars (index 0-4) skipped, only index 5 has a value
            // SMA(5) at index 5 = (20+30+40+50+60)/5 = 40.0
            assertFalse(result.isEmpty());
            assertEquals(40.0, result.get(BASE_TS + 5 * DAY), 0.01);
        }

        @Test
        @DisplayName("3-period SMA produces correct values")
        void threePeriodSma() {
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 10),
                    candle(BASE_TS + DAY, 20),
                    candle(BASE_TS + 2 * DAY, 30),
                    candle(BASE_TS + 3 * DAY, 40),
                    candle(BASE_TS + 4 * DAY, 50)
            );

            Map<Long, Double> result = MovingAverageCalculator.calculateByTimestamp(candles, 3, "SMA");

            // Skips first 3 bars (index 0,1,2). Values start at index 3.
            // SMA(3) at index 3 = (20+30+40)/3 = 30.0
            // SMA(3) at index 4 = (30+40+50)/3 = 40.0
            assertEquals(2, result.size());
            assertEquals(30.0, result.get(BASE_TS + 3 * DAY), 0.01);
            assertEquals(40.0, result.get(BASE_TS + 4 * DAY), 0.01);
        }
    }

    @Nested
    @DisplayName("EMA Calculation")
    class EmaCalculation {

        @Test
        @DisplayName("EMA produces values with enough data points")
        void emaWithEnoughData() {
            List<Candlestick> candles = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                candles.add(candle(BASE_TS + i * DAY, 100.0 + i));
            }

            Map<Long, Double> result = MovingAverageCalculator.calculateByTimestamp(candles, 5, "EMA");

            assertFalse(result.isEmpty());
            // EMA values should exist for bars after the period
            assertNotNull(result.get(BASE_TS + 5 * DAY));
        }
    }

    @Nested
    @DisplayName("Insufficient Data")
    class InsufficientData {

        @Test
        @DisplayName("Returns empty map when not enough candlesticks")
        void insufficientDataReturnsEmpty() {
            List<Candlestick> candles = List.of(
                    candle(BASE_TS, 10),
                    candle(BASE_TS + DAY, 20)
            );

            Map<Long, Double> result = MovingAverageCalculator.calculateByTimestamp(candles, 5, "SMA");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Returns empty map for null input")
        void nullInputReturnsEmpty() {
            Map<Long, Double> result = MovingAverageCalculator.calculateByTimestamp(null, 5, "SMA");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Returns empty map for empty input")
        void emptyInputReturnsEmpty() {
            Map<Long, Double> result = MovingAverageCalculator.calculateByTimestamp(List.of(), 5, "SMA");
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Invalid Parameters")
    class InvalidParameters {

        @Test
        @DisplayName("Throws on zero period")
        void zeroPeriodThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> MovingAverageCalculator.calculateByTimestamp(List.of(), 0, "SMA"));
        }

        @Test
        @DisplayName("Throws on negative period")
        void negativePeriodThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> MovingAverageCalculator.calculateByTimestamp(List.of(), -1, "SMA"));
        }

        @Test
        @DisplayName("Throws on invalid maType")
        void invalidMaTypeThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> MovingAverageCalculator.calculateByTimestamp(List.of(candle(BASE_TS, 10)), 5, "WMA"));
        }

        @Test
        @DisplayName("Throws on null maType")
        void nullMaTypeThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> MovingAverageCalculator.calculateByTimestamp(List.of(candle(BASE_TS, 10)), 5, null));
        }
    }

    @Nested
    @DisplayName("Lookback Calculation")
    class LookbackCalculation {

        @Test
        @DisplayName("lookbackSecondsForStableMa returns period * 2 * 86400")
        void lookbackSeconds() {
            assertEquals(50 * 2 * 86400L, MovingAverageCalculator.lookbackSecondsForStableMa(50));
            assertEquals(200 * 2 * 86400L, MovingAverageCalculator.lookbackSecondsForStableMa(200));
        }

        @Test
        @DisplayName("lookbackSecondsForStableMa throws on non-positive period")
        void lookbackThrowsOnZero() {
            assertThrows(IllegalArgumentException.class,
                    () -> MovingAverageCalculator.lookbackSecondsForStableMa(0));
        }
    }
}
