package com.joakimcolloz.stocker.analysis;

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
 * Integration tests for MACD filter in the combined-scan endpoint.
 */
@DisplayName("MACD Scan Integration Tests")
public class MacdScanIntegrationTest extends AbstractCombinedScanIntegrationTest {

    @BeforeEach
    void setUpMacdData() {
        candlestickRepository.deleteAll();

        List<Candlestick> testData = new ArrayList<>();
        long baseTimestamp = humanReadableToTimestamp("2024-01-01 09:00:00");

        // MACD.ST - 60-day uptrend (should produce positive MACD)
        for (int i = 0; i < 60; i++) {
            Candlestick c = new Candlestick();
            c.setSymbol("MACD.ST");
            c.setTimestamp(baseTimestamp + (i * 86400));
            c.setOpen(100.0 + (i * 2.0));
            c.setHigh(105.0 + (i * 2.0));
            c.setLow(99.0 + (i * 2.0));
            c.setClose(104.0 + (i * 2.0));
            c.setVolume(500000L);
            testData.add(c);
        }

        // BEAR.ST - 60-day downtrend (should produce negative MACD)
        for (int i = 0; i < 60; i++) {
            Candlestick c = new Candlestick();
            c.setSymbol("BEAR.ST");
            c.setTimestamp(baseTimestamp + (i * 86400));
            c.setOpen(300.0 - (i * 2.0));
            c.setHigh(305.0 - (i * 2.0));
            c.setLow(295.0 - (i * 2.0));
            c.setClose(297.0 - (i * 2.0));
            c.setVolume(500000L);
            testData.add(c);
        }

        candlestickRepository.saveAll(testData);
    }

    @Test
    @DisplayName("Should find stocks with MACD above zero")
    void testMacdAboveZeroFilter() throws Exception {
        CombinedScanRequest request = new CombinedScanRequest();
        request.setFilters(List.of(createMACDFilter("above_zero")));
        request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
        request.setEndTimestamp(humanReadableToTimestamp("2024-03-05 23:59:59"));

        mockMvc.perform(post("/api/analysis/combined-scan")
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Should include macd, signal, histogram in filter evidence")
    void testMacdFilterEvidenceContainsAllValues() throws Exception {
        CombinedScanRequest request = new CombinedScanRequest();
        request.setFilters(List.of(createMACDFilter("above_zero")));
        request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
        request.setEndTimestamp(humanReadableToTimestamp("2024-03-05 23:59:59"));

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

        // Verify evidence contains all three MACD values
        for (CombinedFilterResult r : results) {
            Map<String, Double> values = r.getFilters().get(0).getValues();
            assertTrue(values.containsKey("macd"), "Evidence should contain macd");
            assertTrue(values.containsKey("signal"), "Evidence should contain signal");
            assertTrue(values.containsKey("histogram"), "Evidence should contain histogram");
        }
    }

    @Test
    @DisplayName("Should find BEAR.ST with MACD below zero on downtrend")
    void testMacdBelowZeroOnDowntrend() throws Exception {
        CombinedScanRequest request = new CombinedScanRequest();
        request.setFilters(List.of(createMACDFilter("below_zero")));
        request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
        request.setEndTimestamp(humanReadableToTimestamp("2024-03-05 23:59:59"));

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

        boolean foundBear = false;
        for (CombinedFilterResult r : results) {
            if ("BEAR.ST".equals(r.getSymbol())) {
                foundBear = true;
                break;
            }
        }
        assertTrue(foundBear, "BEAR.ST should match below_zero condition");
    }

    @Test
    @DisplayName("Should combine MACD and HIGH_VOLUME filters")
    void testCombinedMacdAndHighVolume() throws Exception {
        // Add a high-volume candle to MACD.ST
        long baseTimestamp = humanReadableToTimestamp("2024-01-01 09:00:00");
        Candlestick spike = new Candlestick();
        spike.setSymbol("MACD.ST");
        spike.setTimestamp(baseTimestamp + (60 * 86400));
        spike.setOpen(220.0);
        spike.setHigh(230.0);
        spike.setLow(218.0);
        spike.setClose(228.0);
        spike.setVolume(5000000L);
        candlestickRepository.save(spike);

        CombinedScanRequest request = new CombinedScanRequest();
        request.setFilters(List.of(
                createHighVolumeFilter(50),
                createMACDFilter("above_zero")
        ));
        request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
        request.setEndTimestamp(humanReadableToTimestamp("2024-03-10 23:59:59"));

        mockMvc.perform(post("/api/analysis/combined-scan")
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Should handle MACD crossover_above condition")
    void testMacdCrossoverAbove() throws Exception {
        CombinedScanRequest request = new CombinedScanRequest();
        request.setFilters(List.of(createMACDFilter("crossover_above")));
        request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
        request.setEndTimestamp(humanReadableToTimestamp("2024-03-05 23:59:59"));

        mockMvc.perform(post("/api/analysis/combined-scan")
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Should return empty result when insufficient data for MACD")
    void testMacdInsufficientData() throws Exception {
        candlestickRepository.deleteAll();

        // Insert only 10 days of data - not enough for MACD
        List<Candlestick> shortData = new ArrayList<>();
        long baseTimestamp = humanReadableToTimestamp("2024-01-01 09:00:00");
        for (int i = 0; i < 10; i++) {
            Candlestick c = new Candlestick();
            c.setSymbol("SHORT.ST");
            c.setTimestamp(baseTimestamp + (i * 86400));
            c.setOpen(100.0 + i);
            c.setHigh(105.0 + i);
            c.setLow(95.0 + i);
            c.setClose(103.0 + i);
            c.setVolume(500000L);
            shortData.add(c);
        }
        candlestickRepository.saveAll(shortData);

        CombinedScanRequest request = new CombinedScanRequest();
        request.setFilters(List.of(createMACDFilter("above_zero")));
        request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
        request.setEndTimestamp(humanReadableToTimestamp("2024-01-15 23:59:59"));

        mockMvc.perform(post("/api/analysis/combined-scan")
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
