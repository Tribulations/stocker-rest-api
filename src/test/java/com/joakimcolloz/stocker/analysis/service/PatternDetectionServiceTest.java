package com.joakimcolloz.stocker.analysis.service;

import com.joakimcolloz.stocker.analysis.dto.pattern.PatternMatch;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PatternDetectionService Unit Tests")
class PatternDetectionServiceTest {

    private PatternDetectionService service;

    @BeforeEach
    void setUp() {
        service = new PatternDetectionService();
    }

    @Test
    @DisplayName("Should detect valid bullish engulfing pattern")
    void testDetectBullishEngulfing_ValidPattern() {
        long base = 1704067200L;
        List<Candlestick> candles = new ArrayList<>();

        // Bearish candle: open=110, close=100 (bearish: close < open)
        candles.add(new Candlestick(110.0, 112.0, 99.0, 100.0, 1000L, base, "TEST.ST"));
        // Bullish engulfing: open=99 (<= prev close 100), close=112 (>= prev open 110)
        candles.add(new Candlestick(99.0, 113.0, 98.0, 112.0, 1500L, base + 86400, "TEST.ST"));

        List<PatternMatch> matches = service.detectBullishEngulfing(candles);

        assertEquals(1, matches.size());
        PatternMatch match = matches.get(0);
        assertEquals("TEST.ST", match.getSymbol());
        assertEquals("BULLISH_ENGULFING", match.getPatternType());
        assertEquals(base + 86400, match.getTimestamp());
        assertTrue(match.getConfidence() >= 50);
    }

    @Test
    @DisplayName("Should not detect when previous candle is not bearish")
    void testDetectBullishEngulfing_PreviousNotBearish() {
        long base = 1704067200L;
        List<Candlestick> candles = new ArrayList<>();

        // Bullish candle (close > open) — not bearish
        candles.add(new Candlestick(100.0, 112.0, 99.0, 110.0, 1000L, base, "TEST.ST"));
        // Another bullish candle
        candles.add(new Candlestick(99.0, 115.0, 98.0, 114.0, 1500L, base + 86400, "TEST.ST"));

        List<PatternMatch> matches = service.detectBullishEngulfing(candles);
        assertTrue(matches.isEmpty());
    }

    @Test
    @DisplayName("Should not detect when body does not engulf")
    void testDetectBullishEngulfing_NotEngulfing() {
        long base = 1704067200L;
        List<Candlestick> candles = new ArrayList<>();

        // Bearish candle: open=110, close=100
        candles.add(new Candlestick(110.0, 112.0, 99.0, 100.0, 1000L, base, "TEST.ST"));
        // Bullish but does NOT engulf: open=101 (> prev close 100) — fails open <= prev.close
        candles.add(new Candlestick(101.0, 112.0, 100.0, 109.0, 1500L, base + 86400, "TEST.ST"));

        List<PatternMatch> matches = service.detectBullishEngulfing(candles);
        assertTrue(matches.isEmpty());
    }

    @Test
    @DisplayName("Should return empty for empty input")
    void testDetectBullishEngulfing_EmptyList() {
        List<PatternMatch> matches = service.detectBullishEngulfing(new ArrayList<>());
        assertTrue(matches.isEmpty());
    }

    @Test
    @DisplayName("Should return empty for single candle")
    void testDetectBullishEngulfing_SingleCandle() {
        List<Candlestick> candles = List.of(
                new Candlestick(100.0, 105.0, 95.0, 102.0, 1000L, 1704067200L, "TEST.ST")
        );
        List<PatternMatch> matches = service.detectBullishEngulfing(candles);
        assertTrue(matches.isEmpty());
    }

    @Test
    @DisplayName("Should detect patterns per symbol independently")
    void testDetectBullishEngulfing_MultipleSymbols() {
        long base = 1704067200L;
        List<Candlestick> candles = new ArrayList<>();

        // Symbol A — valid engulfing
        candles.add(new Candlestick(110.0, 112.0, 99.0, 100.0, 1000L, base, "AAA.ST"));
        candles.add(new Candlestick(99.0, 113.0, 98.0, 112.0, 1500L, base + 86400, "AAA.ST"));

        // Symbol B — no engulfing (both bullish)
        candles.add(new Candlestick(100.0, 105.0, 99.0, 104.0, 1000L, base, "BBB.ST"));
        candles.add(new Candlestick(104.0, 110.0, 103.0, 109.0, 1500L, base + 86400, "BBB.ST"));

        List<PatternMatch> matches = service.detectBullishEngulfing(candles);

        assertEquals(1, matches.size());
        assertEquals("AAA.ST", matches.get(0).getSymbol());
    }

    @Test
    @DisplayName("Should give higher confidence for large engulfing body")
    void testDetectBullishEngulfing_HighConfidence() {
        long base = 1704067200L;
        List<Candlestick> candles = new ArrayList<>();

        // Small bearish candle: body = 2 (open=102, close=100)
        candles.add(new Candlestick(102.0, 103.0, 99.0, 100.0, 1000L, base, "TEST.ST"));
        // Large bullish engulfing: body = 15, small shadows, higher volume
        // curr body (15) >= 1.5 * prev body (2) = 3 → +20
        // upper shadow = 115.1 - 115 = 0.1, lower shadow = 100 - 99.9 = 0.1, both < 1.5 → +15
        // volume 2000 > 1000 → +15
        candles.add(new Candlestick(100.0, 115.1, 99.9, 115.0, 2000L, base + 86400, "TEST.ST"));

        List<PatternMatch> matches = service.detectBullishEngulfing(candles);

        assertEquals(1, matches.size());
        // base 50 + 20 + 15 + 15 = 100, capped at 99
        assertEquals(99, matches.get(0).getConfidence());
    }
}
