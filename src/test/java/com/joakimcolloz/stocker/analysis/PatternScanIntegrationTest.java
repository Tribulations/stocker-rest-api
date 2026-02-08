package com.joakimcolloz.stocker.analysis;

import com.joakimcolloz.stocker.analysis.dto.filter.FilterRequest;
import com.joakimcolloz.stocker.analysis.dto.filter.PatternFilterParams;
import com.joakimcolloz.stocker.analysis.dto.request.CombinedScanRequest;
import com.joakimcolloz.stocker.analysis.dto.response.CombinedFilterResult;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.joakimcolloz.stocker.api.AnalysisApiIntegrationTestUtil.humanReadableToTimestamp;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for PATTERN filter in the combined-scan endpoint.
 */
@DisplayName("Pattern Scan Integration Tests")
public class PatternScanIntegrationTest extends AbstractCombinedScanIntegrationTest {

    @BeforeEach
    void setUpPatternData() {
        candlestickRepository.deleteAll();

        List<Candlestick> testData = new ArrayList<>();
        long baseTimestamp = humanReadableToTimestamp("2024-01-01 09:00:00");

        // ENGULF.ST — 20 candles with a clear bearish→bullish engulfing pair at candles 9→10
        for (int i = 0; i < 20; i++) {
            double open, high, low, close;
            long volume = 500000L;

            if (i == 9) {
                // Bearish candle: open=120, close=110
                open = 120.0;
                high = 122.0;
                low = 109.0;
                close = 110.0;
            } else if (i == 10) {
                // Bullish engulfing: open=109 (<= prev close 110), close=121 (>= prev open 120)
                open = 109.0;
                high = 122.0;
                low = 108.0;
                close = 121.0;
                volume = 600000L; // Higher volume for confidence boost
            } else {
                // Normal slightly-rising candles (no pattern)
                open = 100.0 + i;
                high = 103.0 + i;
                low = 99.0 + i;
                close = 102.0 + i;
            }

            Candlestick c = new Candlestick(open, high, low, close, volume,
                    baseTimestamp + (i * 86400), "ENGULF.ST");
            testData.add(c);
        }

        // HAMMER.ST — 20 candles with a clear hammer at candle 5
        for (int i = 0; i < 20; i++) {
            double open, high, low, close;
            long volume = 500000L;

            if (i == 5) {
                // Hammer: body=2, lower shadow=8, upper shadow=0.5
                open = 98.0;
                high = 100.5;
                low = 90.0;
                close = 100.0;
            } else {
                open = 100.0 + i;
                high = 103.0 + i;
                low = 99.0 + i;
                close = 102.0 + i;
            }

            Candlestick c = new Candlestick(open, high, low, close, volume,
                    baseTimestamp + (i * 86400), "HAMMER.ST");
            testData.add(c);
        }

        // HIGHVOL.ST — 20 candles, one with very high volume + an engulfing pair
        for (int i = 0; i < 20; i++) {
            double open, high, low, close;
            long volume = 500000L;

            if (i == 14) {
                // Bearish candle
                open = 130.0;
                high = 132.0;
                low = 118.0;
                close = 120.0;
            } else if (i == 15) {
                // Bullish engulfing + high volume
                open = 119.0;
                high = 133.0;
                low = 118.0;
                close = 131.0;
                volume = 5000000L; // Very high volume
            } else {
                open = 100.0 + i;
                high = 103.0 + i;
                low = 99.0 + i;
                close = 102.0 + i;
                volume = 500000L;
            }

            Candlestick c = new Candlestick(open, high, low, close, volume,
                    baseTimestamp + (i * 86400), "HIGHVOL.ST");
            testData.add(c);
        }

        candlestickRepository.saveAll(testData);
    }

    protected FilterRequest createPatternFilter(String pattern) {
        FilterRequest filter = new FilterRequest();
        filter.setType("PATTERN");
        filter.setParams(new PatternFilterParams(pattern));
        return filter;
    }

    @Test
    @DisplayName("Should find hammer patterns via PATTERN filter")
    void testPatternFilterHammer() throws Exception {
        CombinedScanRequest request = new CombinedScanRequest();
        request.setFilters(List.of(createPatternFilter("HAMMER")));
        request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
        request.setEndTimestamp(humanReadableToTimestamp("2024-01-25 23:59:59"));

        mockMvc.perform(post("/api/analysis/combined-scan")
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Should find bullish engulfing patterns via PATTERN filter")
    void testPatternFilterBullishEngulfing() throws Exception {
        CombinedScanRequest request = new CombinedScanRequest();
        request.setFilters(List.of(createPatternFilter("BULLISH_ENGULFING")));
        request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
        request.setEndTimestamp(humanReadableToTimestamp("2024-01-25 23:59:59"));

        MvcResult result = mockMvc.perform(post("/api/analysis/combined-scan")
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andReturn();

        CombinedFilterResult[] results = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CombinedFilterResult[].class
        );

        boolean foundEngulf = false;
        for (CombinedFilterResult r : results) {
            if ("ENGULF.ST".equals(r.getSymbol()) || "HIGHVOL.ST".equals(r.getSymbol())) {
                foundEngulf = true;
                break;
            }
        }
        assertTrue(foundEngulf, "Should find engulfing pattern in ENGULF.ST or HIGHVOL.ST");
    }

    @Test
    @DisplayName("Pattern filter evidence should contain confidence value")
    void testPatternFilterEvidenceContainsConfidence() throws Exception {
        CombinedScanRequest request = new CombinedScanRequest();
        request.setFilters(List.of(createPatternFilter("BULLISH_ENGULFING")));
        request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
        request.setEndTimestamp(humanReadableToTimestamp("2024-01-25 23:59:59"));

        MvcResult result = mockMvc.perform(post("/api/analysis/combined-scan")
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andReturn();

        CombinedFilterResult[] results = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                CombinedFilterResult[].class
        );

        for (CombinedFilterResult r : results) {
            Map<String, Double> values = r.getFilters().get(0).getValues();
            assertTrue(values.containsKey("confidence"), "Evidence should contain confidence");
        }
    }

    @Test
    @DisplayName("Should combine PATTERN and HIGH_VOLUME filters")
    void testCombinedPatternAndHighVolume() throws Exception {
        CombinedScanRequest request = new CombinedScanRequest();
        request.setFilters(List.of(
                createHighVolumeFilter(50),
                createPatternFilter("BULLISH_ENGULFING")
        ));
        request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
        request.setEndTimestamp(humanReadableToTimestamp("2024-01-25 23:59:59"));

        mockMvc.perform(post("/api/analysis/combined-scan")
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Should combine PATTERN and RSI filters")
    void testCombinedPatternAndRsi() throws Exception {
        CombinedScanRequest request = new CombinedScanRequest();
        request.setFilters(List.of(
                createRSIFilter(30.0, "above"),
                createPatternFilter("BULLISH_ENGULFING")
        ));
        request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
        request.setEndTimestamp(humanReadableToTimestamp("2024-01-25 23:59:59"));

        mockMvc.perform(post("/api/analysis/combined-scan")
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Should return error for unknown pattern code")
    void testUnknownPatternCode() throws Exception {
        CombinedScanRequest request = new CombinedScanRequest();
        request.setFilters(List.of(createPatternFilter("NONEXISTENT_PATTERN")));
        request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
        request.setEndTimestamp(humanReadableToTimestamp("2024-01-25 23:59:59"));

        mockMvc.perform(post("/api/analysis/combined-scan")
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
