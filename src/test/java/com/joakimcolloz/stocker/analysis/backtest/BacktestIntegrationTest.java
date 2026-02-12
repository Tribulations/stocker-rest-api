package com.joakimcolloz.stocker.analysis.backtest;

import com.joakimcolloz.stocker.analysis.AbstractCombinedScanIntegrationTest;
import com.joakimcolloz.stocker.analysis.backtest.dto.BacktestRequest;
import com.joakimcolloz.stocker.analysis.backtest.dto.ExitRule;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;

import static com.joakimcolloz.stocker.api.AnalysisApiIntegrationTestUtil.humanReadableToTimestamp;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Backtest Integration Tests")
public class BacktestIntegrationTest extends AbstractCombinedScanIntegrationTest {

    @BeforeEach
    void setUp() {
        candlestickRepository.deleteAll();
    }

    private void insertBacktestData() {
        List<Candlestick> testData = new ArrayList<>();
        long baseTimestamp = humanReadableToTimestamp("2024-01-01 09:00:00");

        // ABB.ST - Strong uptrend with RSI context (28+ bars for stable RSI)
        for (int i = 0; i < 28; i++) {
            Candlestick c = new Candlestick();
            c.setSymbol("ABB.ST");
            c.setTimestamp(baseTimestamp + (i * 86400));
            c.setOpen(100.0 + (i * 2.0));
            c.setHigh(105.0 + (i * 2.0));
            c.setLow(99.0 + (i * 2.0));
            c.setClose(104.0 + (i * 2.0));
            c.setVolume(500000L);
            testData.add(c);
        }

        // Day 28: Small pullback to bring RSI down slightly, then spike
        Candlestick pullback = new Candlestick();
        pullback.setSymbol("ABB.ST");
        pullback.setTimestamp(baseTimestamp + (28 * 86400));
        pullback.setOpen(156.0);
        pullback.setHigh(158.0);
        pullback.setLow(150.0);
        pullback.setClose(152.0);
        pullback.setVolume(500000L);
        testData.add(pullback);

        // Day 29: Big rally after pullback - high enough to trigger take-profit if any
        Candlestick rally = new Candlestick();
        rally.setSymbol("ABB.ST");
        rally.setTimestamp(baseTimestamp + (29 * 86400));
        rally.setOpen(153.0);
        rally.setHigh(175.0);
        rally.setLow(152.0);
        rally.setClose(170.0);
        rally.setVolume(1500000L); // High volume spike
        testData.add(rally);

        // Day 30: Another day
        Candlestick day30 = new Candlestick();
        day30.setSymbol("ABB.ST");
        day30.setTimestamp(baseTimestamp + (30 * 86400));
        day30.setOpen(170.0);
        day30.setHigh(180.0);
        day30.setLow(168.0);
        day30.setClose(175.0);
        day30.setVolume(600000L);
        testData.add(day30);

        candlestickRepository.saveAll(testData);
    }

    @Nested
    @DisplayName("Full Roundtrip Tests")
    class FullRoundtrip {

        @Test
        @DisplayName("Should execute backtest and return result with trades")
        void fullBacktestRoundtrip() throws Exception {
            insertBacktestData();

            BacktestRequest request = new BacktestRequest();
            request.setEntryConditions(List.of(createRSIFilter(50, "above")));
            request.setExitRule(new ExitRule(5.0, 10.0, null));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-15 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-02-05 23:59:59"));
            request.setSymbols(List.of("ABB.ST"));

            mockMvc.perform(post("/api/analysis/backtest")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalTrades").isNumber())
                    .andExpect(jsonPath("$.winRate").isNumber())
                    .andExpect(jsonPath("$.trades").isArray())
                    .andExpect(jsonPath("$.startTimestamp").isNumber())
                    .andExpect(jsonPath("$.endTimestamp").isNumber())
                    .andExpect(jsonPath("$.averageHoldingDays").isNumber())
                    .andExpect(jsonPath("$.maxDrawdownPercent").isNumber());
        }

        @Test
        @DisplayName("Should return 0 trades for empty database")
        void emptyDatabaseReturnsZeroTrades() throws Exception {
            BacktestRequest request = new BacktestRequest();
            request.setEntryConditions(List.of(createRSIFilter(50, "above")));
            request.setExitRule(new ExitRule(5.0, 10.0, null));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-02-01 23:59:59"));

            mockMvc.perform(post("/api/analysis/backtest")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalTrades").value(0))
                    .andExpect(jsonPath("$.trades", hasSize(0)))
                    .andExpect(jsonPath("$.winRate").value(0.0));
        }

        @Test
        @DisplayName("Should return result with HIGH_VOLUME entry condition")
        void backtestWithHighVolume() throws Exception {
            insertBacktestData();

            BacktestRequest request = new BacktestRequest();
            request.setEntryConditions(List.of(createHighVolumeFilter(100)));
            request.setExitRule(new ExitRule(5.0, 10.0, 5));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-02-05 23:59:59"));

            mockMvc.perform(post("/api/analysis/backtest")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.trades").isArray());
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class Validation {

        @Test
        @DisplayName("Should return 400 when entry conditions are empty")
        void emptyEntryConditions() throws Exception {
            BacktestRequest request = new BacktestRequest();
            request.setEntryConditions(new ArrayList<>());
            request.setExitRule(new ExitRule(5.0, null, null));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-02-01 23:59:59"));

            mockMvc.perform(post("/api/analysis/backtest")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when time range is invalid")
        void invalidTimeRange() throws Exception {
            BacktestRequest request = new BacktestRequest();
            request.setEntryConditions(List.of(createRSIFilter(50, "above")));
            request.setExitRule(new ExitRule(5.0, null, null));
            request.setStartTimestamp(humanReadableToTimestamp("2024-02-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));

            mockMvc.perform(post("/api/analysis/backtest")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when exit rule is missing")
        void missingExitRule() throws Exception {
            // Manually build JSON to avoid ExitRule constructor validation
            String json = """
                    {
                      "entryConditions": [{"type": "RSI", "params": {"threshold": 50, "condition": "above"}}],
                      "exitRule": null,
                      "startTimestamp": 1704067200,
                      "endTimestamp": 1706745600
                    }
                    """;

            mockMvc.perform(post("/api/analysis/backtest")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 401 when API key is missing")
        void missingApiKey() throws Exception {
            BacktestRequest request = new BacktestRequest();
            request.setEntryConditions(List.of(createRSIFilter(50, "above")));
            request.setExitRule(new ExitRule(5.0, null, null));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-02-01 23:59:59"));

            mockMvc.perform(post("/api/analysis/backtest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }
}
