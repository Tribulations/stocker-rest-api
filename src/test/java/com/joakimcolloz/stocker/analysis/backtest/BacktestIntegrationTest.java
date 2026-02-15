package com.joakimcolloz.stocker.analysis.backtest;

import com.joakimcolloz.stocker.analysis.AbstractCombinedScanIntegrationTest;
import com.joakimcolloz.stocker.analysis.backtest.dto.BacktestRequest;
import com.joakimcolloz.stocker.analysis.backtest.dto.EntryRule;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Backtest Integration Tests")
public class BacktestIntegrationTest extends AbstractCombinedScanIntegrationTest {

    @BeforeEach
    void setUp() {
        candlestickRepository.deleteAll();
    }

    private void insertPatternBacktestData() {
        List<Candlestick> testData = new ArrayList<>();
        long baseTimestamp = humanReadableToTimestamp("2024-01-01 09:00:00");

        // Day 0: normal candle (no hammer)
        Candlestick day0 = new Candlestick();
        day0.setSymbol("ABB.ST");
        day0.setTimestamp(baseTimestamp);
        day0.setOpen(100.0);
        day0.setHigh(105.0);
        day0.setLow(99.0);
        day0.setClose(104.0);
        day0.setVolume(500000L);
        testData.add(day0);

        // Day 1: hammer candle — long lower shadow, tiny upper shadow, small body
        // body=1.0, lowerShadow=4.0 (>2*body), upperShadow=0.2 (<0.3*body)
        Candlestick hammer = new Candlestick();
        hammer.setSymbol("ABB.ST");
        hammer.setTimestamp(baseTimestamp + 86400);
        hammer.setOpen(99.0);
        hammer.setHigh(100.2);
        hammer.setLow(95.0);
        hammer.setClose(100.0);
        hammer.setVolume(500000L);
        testData.add(hammer);

        // Day 2: rally (high enough to trigger take-profit)
        Candlestick rally = new Candlestick();
        rally.setSymbol("ABB.ST");
        rally.setTimestamp(baseTimestamp + 2 * 86400);
        rally.setOpen(101.0);
        rally.setHigh(115.0);
        rally.setLow(100.0);
        rally.setClose(112.0);
        rally.setVolume(600000L);
        testData.add(rally);

        // Day 3: continuation
        Candlestick day3 = new Candlestick();
        day3.setSymbol("ABB.ST");
        day3.setTimestamp(baseTimestamp + 3 * 86400);
        day3.setOpen(112.0);
        day3.setHigh(116.0);
        day3.setLow(110.0);
        day3.setClose(114.0);
        day3.setVolume(500000L);
        testData.add(day3);

        candlestickRepository.saveAll(testData);
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
        @DisplayName("Should execute backtest and return result with trades and capital fields")
        void fullBacktestRoundtrip() throws Exception {
            insertBacktestData();

            BacktestRequest request = new BacktestRequest();
            request.setEntryConditions(List.of(createRSIFilter(50, "above")));
            request.setExitRule(new ExitRule(5.0, 10.0, null, null));
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
                    .andExpect(jsonPath("$.maxDrawdownPercent").isNumber())
                    .andExpect(jsonPath("$.startingCapital").value(10000.0))
                    .andExpect(jsonPath("$.finalCapital").isNumber());
        }

        @Test
        @DisplayName("Should use custom starting capital when provided")
        void customStartingCapital() throws Exception {
            insertBacktestData();

            BacktestRequest request = new BacktestRequest();
            request.setEntryConditions(List.of(createRSIFilter(50, "above")));
            request.setExitRule(new ExitRule(5.0, 10.0, null, null));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-15 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-02-05 23:59:59"));
            request.setSymbols(List.of("ABB.ST"));
            request.setStartingCapital(50_000.0);

            mockMvc.perform(post("/api/analysis/backtest")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.startingCapital").value(50000.0))
                    .andExpect(jsonPath("$.finalCapital").isNumber());
        }

        @Test
        @DisplayName("Should return 0 trades for empty database")
        void emptyDatabaseReturnsZeroTrades() throws Exception {
            BacktestRequest request = new BacktestRequest();
            request.setEntryConditions(List.of(createRSIFilter(50, "above")));
            request.setExitRule(new ExitRule(5.0, 10.0, null, null));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-02-01 23:59:59"));

            mockMvc.perform(post("/api/analysis/backtest")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalTrades").value(0))
                    .andExpect(jsonPath("$.trades", hasSize(0)))
                    .andExpect(jsonPath("$.winRate").value(0.0))
                    .andExpect(jsonPath("$.startingCapital").value(10000.0))
                    .andExpect(jsonPath("$.finalCapital").value(10000.0));
        }

        @Test
        @DisplayName("Should expose supported execution config options")
        void supportedEndpointIncludesExecutionConfig() throws Exception {
            mockMvc.perform(get("/api/analysis/backtest/supported")
                            .header("X-API-Key", "test-api-key"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.execution").exists())
                    .andExpect(jsonPath("$.execution.mode", hasItems("DAILY_ONLY", "HYBRID_INTRADAY")))
                    .andExpect(jsonPath("$.execution.intradayInterval", hasItem("5m")))
                    .andExpect(jsonPath("$.execution.defaults.mode").value("DAILY_ONLY"))
                    .andExpect(jsonPath("$.execution.defaults.conservativeFillMode").value(true));
        }

        @Test
        @DisplayName("Should expose backtest description endpoint")
        void descriptionEndpointExists() throws Exception {
            mockMvc.perform(get("/api/analysis/backtest/description")
                            .header("X-API-Key", "test-api-key"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.positions").value("LONG_ONLY"))
                    .andExpect(jsonPath("$.executionModes.DAILY_ONLY").exists())
                    .andExpect(jsonPath("$.executionModes.HYBRID_INTRADAY").exists())
                    .andExpect(jsonPath("$.intraday.interval").value("5m"));
        }

        @Test
        @DisplayName("Should return result with PATTERN entry condition (HAMMER)")
        void backtestWithPattern() throws Exception {
            insertPatternBacktestData();

            BacktestRequest request = new BacktestRequest();
            request.setEntryConditions(List.of(createPatternFilter("HAMMER")));
            request.setExitRule(new ExitRule(5.0, 10.0, 5, null));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-01-10 23:59:59"));
            request.setSymbols(List.of("ABB.ST"));

            mockMvc.perform(post("/api/analysis/backtest")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalTrades").value(greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.trades").isArray())
                    .andExpect(jsonPath("$.trades", hasSize(greaterThanOrEqualTo(1))))
                    .andExpect(jsonPath("$.startingCapital").isNumber())
                    .andExpect(jsonPath("$.finalCapital").isNumber());
        }

        @Test
        @DisplayName("Should return result with HIGH_VOLUME entry condition")
        void backtestWithHighVolume() throws Exception {
            insertBacktestData();

            BacktestRequest request = new BacktestRequest();
            request.setEntryConditions(List.of(createHighVolumeFilter(100)));
            request.setExitRule(new ExitRule(5.0, 10.0, 5, null));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-02-05 23:59:59"));

            mockMvc.perform(post("/api/analysis/backtest")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.trades").isArray());
        }

        @Test
        @DisplayName("Should execute backtest with LIMIT_ORDER entry mode")
        void backtestWithLimitOrder() throws Exception {
            insertBacktestData();

            BacktestRequest request = new BacktestRequest();
            request.setEntryConditions(List.of(createRSIFilter(50, "above")));
            request.setExitRule(new ExitRule(5.0, 10.0, null, null));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-15 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-02-05 23:59:59"));
            request.setSymbols(List.of("ABB.ST"));
            request.setEntryRule(new EntryRule("LIMIT_ORDER", 1.0, 0.0, 3, null));

            mockMvc.perform(post("/api/analysis/backtest")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalTrades").isNumber())
                    .andExpect(jsonPath("$.trades").isArray())
                    .andExpect(jsonPath("$.startingCapital").value(10000.0))
                    .andExpect(jsonPath("$.finalCapital").isNumber());
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
            request.setExitRule(new ExitRule(5.0, null, null, null));
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
            request.setExitRule(new ExitRule(5.0, null, null, null));
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
            request.setExitRule(new ExitRule(5.0, null, null, null));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-02-01 23:59:59"));

            mockMvc.perform(post("/api/analysis/backtest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("MA_CROSSOVER and Exit Conditions")
    class MaCrossoverAndExitConditions {

        @Test
        @DisplayName("Should execute backtest with MA_CROSSOVER entry condition")
        void backtestWithMaCrossoverEntry() throws Exception {
            insertBacktestData();

            BacktestRequest request = new BacktestRequest();
            request.setEntryConditions(List.of(
                    createMaCrossoverFilter(5, "SMA", "price_above_ma")));
            request.setExitRule(new ExitRule(5.0, 10.0, 10, null));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-15 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-02-05 23:59:59"));
            request.setSymbols(List.of("ABB.ST"));

            mockMvc.perform(post("/api/analysis/backtest")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalTrades").isNumber())
                    .andExpect(jsonPath("$.trades").isArray())
                    .andExpect(jsonPath("$.startingCapital").value(10000.0))
                    .andExpect(jsonPath("$.finalCapital").isNumber());
        }

        @Test
        @DisplayName("Should execute backtest with exit conditions (RSI exit)")
        void backtestWithExitConditions() throws Exception {
            insertBacktestData();

            BacktestRequest request = new BacktestRequest();
            request.setEntryConditions(List.of(createRSIFilter(90, "above")));
            request.setExitRule(new ExitRule(10.0, null, 20, null));
            request.setExitConditions(List.of(createRSIFilter(60, "below")));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-15 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-02-05 23:59:59"));
            request.setSymbols(List.of("ABB.ST"));

            mockMvc.perform(post("/api/analysis/backtest")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalTrades").isNumber())
                    .andExpect(jsonPath("$.trades").isArray());
        }
    }

    @Nested
    @DisplayName("Trailing Stop-Loss")
    class TrailingStopLossTests {

        @Test
        @DisplayName("Should execute backtest with trailing stop-loss enabled")
        void backtestWithTrailingStopLoss() throws Exception {
            insertBacktestData();

            BacktestRequest request = new BacktestRequest();
            request.setEntryConditions(List.of(createRSIFilter(50, "above")));
            request.setExitRule(new ExitRule(5.0, 15.0, null, true));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-15 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-02-05 23:59:59"));
            request.setSymbols(List.of("ABB.ST"));

            mockMvc.perform(post("/api/analysis/backtest")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalTrades").isNumber())
                    .andExpect(jsonPath("$.trades").isArray())
                    .andExpect(jsonPath("$.startingCapital").value(10000.0))
                    .andExpect(jsonPath("$.finalCapital").isNumber());
        }
    }

    @Nested
    @DisplayName("Limit Order ABOVE Direction")
    class LimitOrderAboveDirection {

        @Test
        @DisplayName("Should execute backtest with LIMIT_ORDER entry mode and ABOVE direction")
        void backtestWithLimitOrderAbove() throws Exception {
            insertBacktestData();

            BacktestRequest request = new BacktestRequest();
            request.setEntryConditions(List.of(createRSIFilter(50, "above")));
            request.setExitRule(new ExitRule(5.0, 10.0, null, null));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-15 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-02-05 23:59:59"));
            request.setSymbols(List.of("ABB.ST"));
            request.setEntryRule(new EntryRule("LIMIT_ORDER", 1.0, 0.0, 3, "ABOVE"));

            mockMvc.perform(post("/api/analysis/backtest")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalTrades").isNumber())
                    .andExpect(jsonPath("$.trades").isArray())
                    .andExpect(jsonPath("$.startingCapital").value(10000.0))
                    .andExpect(jsonPath("$.finalCapital").isNumber());
        }
    }
}
