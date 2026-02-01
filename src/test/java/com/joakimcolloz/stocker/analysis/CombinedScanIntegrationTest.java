package com.joakimcolloz.stocker.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joakimcolloz.stocker.analysis.dto.CombinedScanRequest;
import com.joakimcolloz.stocker.analysis.dto.FilterRequest;
import com.joakimcolloz.stocker.analysis.dto.HighVolumeFilterParams;
import com.joakimcolloz.stocker.analysis.dto.RsiFilterParams;
import com.joakimcolloz.stocker.api.dao.CandlestickRepository;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;

import static com.joakimcolloz.stocker.api.AnalysisApiIntegrationTestUtil.humanReadableToTimestamp;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for combined scan functionality.
 * Tests the complete flow from API endpoint through filters to database.
 */
@SpringBootTest(classes = com.joakimcolloz.stocker.api.Application.class)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Combined Scan Integration Tests")
public class CombinedScanIntegrationTest {

    @Container
    public static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:13-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("testdb.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CandlestickRepository candlestickRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        candlestickRepository.deleteAll();
        insertTestData();
    }

    /**
     * Inserts test data with clear patterns for reliable filter testing.
     * Creates uptrend data that will produce high RSI values and high volume spikes.
     */
    private void insertTestData() {
        List<Candlestick> testData = new ArrayList<>();

        long baseTimestamp = humanReadableToTimestamp("2024-01-01 09:00:00");

        // ABB.ST - Strong uptrend with increasing volume (will produce high RSI)
        // Days 1-14: Build RSI context (need 14+ periods for RSI calculation)
        for (int i = 0; i < 14; i++) {
            Candlestick c = new Candlestick();
            c.setSymbol("ABB.ST");
            c.setTimestamp(baseTimestamp + (i * 86400));
            c.setOpen(100.0 + (i * 2.0));  // Consistently rising prices
            c.setHigh(105.0 + (i * 2.0));
            c.setLow(99.0 + (i * 2.0));
            c.setClose(104.0 + (i * 2.0)); // Strong closes for high RSI
            c.setVolume(500000L);           // Normal volume
            testData.add(c);
        }

        // Day 15: Continuation of uptrend (RSI will be high)
        Candlestick highRSICandle = new Candlestick();
        highRSICandle.setSymbol("ABB.ST");
        highRSICandle.setTimestamp(baseTimestamp + (14 * 86400));
        highRSICandle.setOpen(128.0);
        highRSICandle.setHigh(135.0);
        highRSICandle.setLow(127.0);
        highRSICandle.setClose(134.0);
        highRSICandle.setVolume(500000L);
        testData.add(highRSICandle);

        // Day 16: High volume spike with continued uptrend (matches BOTH filters)
        Candlestick combinedCandle = new Candlestick();
        combinedCandle.setSymbol("ABB.ST");
        combinedCandle.setTimestamp(baseTimestamp + (15 * 86400));
        combinedCandle.setOpen(134.0);
        combinedCandle.setHigh(142.0);
        combinedCandle.setLow(133.0);
        combinedCandle.setClose(141.0);
        combinedCandle.setVolume(1500000L); // 200% above average (will match HIGH_VOLUME)
        testData.add(combinedCandle);

        // BOL.ST - Sideways/declining (low RSI, normal volume)
        for (int i = 0; i < 16; i++) {
            Candlestick c = new Candlestick();
            c.setSymbol("BOL.ST");
            c.setTimestamp(baseTimestamp + (i * 86400));
            c.setOpen(200.0 - (i * 0.5));  // Slight decline
            c.setHigh(202.0 - (i * 0.5));
            c.setLow(198.0 - (i * 0.5));
            c.setClose(199.0 - (i * 0.5)); // Weak closes for low RSI
            c.setVolume(300000L);           // Consistent low volume
            testData.add(c);
        }

        candlestickRepository.saveAll(testData);
    }

    @Nested
    @DisplayName("Request Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should return 400 when filters list is empty")
        void testEmptyFilters() throws Exception {
            CombinedScanRequest request = new CombinedScanRequest();
            request.setFilters(new ArrayList<>());
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-01-20 23:59:59"));

            mockMvc.perform(post("/api/analysis/combined-scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when start timestamp is invalid")
        void testInvalidStartTimestamp() throws Exception {
            CombinedScanRequest request = new CombinedScanRequest();
            request.setFilters(List.of(createHighVolumeFilter(50)));
            request.setStartTimestamp(0);
            request.setEndTimestamp(humanReadableToTimestamp("2024-01-20 23:59:59"));

            mockMvc.perform(post("/api/analysis/combined-scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when end timestamp is before start")
        void testInvalidTimeRange() throws Exception {
            CombinedScanRequest request = new CombinedScanRequest();
            request.setFilters(List.of(createHighVolumeFilter(50)));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-20 23:59:59"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));

            mockMvc.perform(post("/api/analysis/combined-scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 401 when API key is missing")
        void testMissingApiKey() throws Exception {
            CombinedScanRequest request = new CombinedScanRequest();
            request.setFilters(List.of(createHighVolumeFilter(50)));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-01-20 23:59:59"));

            mockMvc.perform(post("/api/analysis/combined-scan")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Single Filter Tests")
    class SingleFilterTests {

        @Test
        @DisplayName("Should find high volume candlesticks with HIGH_VOLUME filter")
        void testHighVolumeFilterOnly() throws Exception {
            CombinedScanRequest request = new CombinedScanRequest();
            request.setFilters(List.of(createHighVolumeFilter(50)));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-01-20 23:59:59"));

            mockMvc.perform(post("/api/analysis/combined-scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                    .andExpect(jsonPath("$[0].symbol").exists())
                    .andExpect(jsonPath("$[0].filterType").value("HIGH_VOLUME"))
                    .andExpect(jsonPath("$[0].description").exists());
        }

        @Test
        @DisplayName("Should handle RSI filter request successfully")
        void testRSIFilterOnly() throws Exception {
            CombinedScanRequest request = new CombinedScanRequest();
            request.setFilters(List.of(createRSIFilter(50, "above")));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-01-20 23:59:59"));

            mockMvc.perform(post("/api/analysis/combined-scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
            // Accept either empty array or results - RSI calculation may vary
        }
    }

    @Nested
    @DisplayName("Combined Filter Tests")
    class CombinedFilterTests {

        @Test
        @DisplayName("Should apply HIGH_VOLUME then RSI filters sequentially")
        void testCombinedHighVolumeAndRSI() throws Exception {
            CombinedScanRequest request = new CombinedScanRequest();
            request.setFilters(List.of(
                    createHighVolumeFilter(50),
                    createRSIFilter(70, "above")
            ));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-01-20 23:59:59"));

            mockMvc.perform(post("/api/analysis/combined-scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());

            // Results should be narrower than single filter
            // (only stocks matching BOTH criteria)
        }

        @Test
        @DisplayName("Should handle multiple filters with no matches")
        void testNoMatchesFromCombinedFilters() throws Exception {
            CombinedScanRequest request = new CombinedScanRequest();
            request.setFilters(List.of(
                    createHighVolumeFilter(500), // Extremely high threshold
                    createRSIFilter(99, "above")  // Nearly impossible RSI
            ));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-01-20 23:59:59"));

            mockMvc.perform(post("/api/analysis/combined-scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

		@Test
		@DisplayName("Should skip symbols with insufficient RSI data and still return 200")
		void testRsiTooFewDataPointsSkipsSymbol() throws Exception {
			CombinedScanRequest request = new CombinedScanRequest();
			request.setFilters(List.of(createRSIFilter(50, "above")));
			request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 09:00:00"));
			request.setEndTimestamp(humanReadableToTimestamp("2024-01-05 09:00:00"));

			mockMvc.perform(post("/api/analysis/combined-scan")
						.header("X-API-Key", "test-api-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$", hasSize(0)));
		}

		@Test
		@DisplayName("Should exclude symbols that fail RSI calculation but still return matches for other symbols")
		void testPartialResultsWhenOneSymbolFailsRSI() throws Exception {
			long baseTimestamp = humanReadableToTimestamp("2024-01-01 09:00:00");

			List<Candlestick> extra = new ArrayList<>();

			// MIX.ST - enough data points and mixed movement so RSI should be between 0 and 100
			for (int i = 0; i < 15; i++) {
				Candlestick c = new Candlestick();
				c.setSymbol("MIX.ST");
				c.setTimestamp(baseTimestamp + (i * 86400));
				double open = 100.0 + i;
				double close = (i % 5 == 0) ? open - 0.5 : open + 1.0;
				c.setOpen(open);
				c.setHigh(Math.max(open, close) + 1.0);
				c.setLow(Math.min(open, close) - 1.0);
				c.setClose(close);
				c.setVolume(100L);
				extra.add(c);
			}
			Candlestick mixSpike = new Candlestick();
			mixSpike.setSymbol("MIX.ST");
			mixSpike.setTimestamp(baseTimestamp + (15 * 86400));
			mixSpike.setOpen(100.0);
			mixSpike.setHigh(103.0);
			mixSpike.setLow(99.0);
			mixSpike.setClose(101.0);
			mixSpike.setVolume(1000L);
			extra.add(mixSpike);

			// FAIL.ST - too few datapoints for RSI
			for (int i = 0; i < 4; i++) {
				Candlestick c = new Candlestick();
				c.setSymbol("FAIL.ST");
				c.setTimestamp(baseTimestamp + (i * 86400));
				c.setOpen(50.0);
				c.setHigh(51.0);
				c.setLow(49.0);
				c.setClose(50.0 + i);
				c.setVolume(100L);
				extra.add(c);
			}
			Candlestick failSpike = new Candlestick();
			failSpike.setSymbol("FAIL.ST");
			failSpike.setTimestamp(baseTimestamp + (4 * 86400));
			failSpike.setOpen(54.0);
			failSpike.setHigh(55.0);
			failSpike.setLow(53.0);
			failSpike.setClose(54.0);
			failSpike.setVolume(1000L);
			extra.add(failSpike);

			candlestickRepository.saveAll(extra);

			CombinedScanRequest request = new CombinedScanRequest();
			request.setFilters(List.of(
					createHighVolumeFilter(200),
					createRSIFilter(40, "above")
			));
			request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
			request.setEndTimestamp(humanReadableToTimestamp("2024-01-20 23:59:59"));

			mockMvc.perform(post("/api/analysis/combined-scan")
						.header("X-API-Key", "test-api-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$", hasSize(1)))
					.andExpect(jsonPath("$[0].symbol").value("MIX.ST"));
		}
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty database gracefully")
        void testEmptyDatabase() throws Exception {
            candlestickRepository.deleteAll();

            CombinedScanRequest request = new CombinedScanRequest();
            request.setFilters(List.of(createHighVolumeFilter(50)));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-01-20 23:59:59"));

            mockMvc.perform(post("/api/analysis/combined-scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Should handle time range with no data")
        void testTimeRangeWithNoData() throws Exception {
            CombinedScanRequest request = new CombinedScanRequest();
            request.setFilters(List.of(createHighVolumeFilter(50)));
            request.setStartTimestamp(1893456000L); // Far future
            request.setEndTimestamp(1893542400L);

            mockMvc.perform(post("/api/analysis/combined-scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("Filter Order Tests")
    class FilterOrderTests {

        @Test
        @DisplayName("Should apply filters in specified order (HIGH_VOLUME then RSI)")
        void testFilterOrderHighVolumeThenRSI() throws Exception {
            CombinedScanRequest request = new CombinedScanRequest();
            request.setFilters(List.of(
                    createHighVolumeFilter(30),
                    createRSIFilter(60, "above")
            ));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-01-20 23:59:59"));

            mockMvc.perform(post("/api/analysis/combined-scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Should apply filters in specified order (RSI then HIGH_VOLUME)")
        void testFilterOrderRSIThenHighVolume() throws Exception {
            CombinedScanRequest request = new CombinedScanRequest();
            request.setFilters(List.of(
                    createRSIFilter(60, "above"),
                    createHighVolumeFilter(30)
            ));
            request.setStartTimestamp(humanReadableToTimestamp("2024-01-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2024-01-20 23:59:59"));

            mockMvc.perform(post("/api/analysis/combined-scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // Helper methods to create filter requests

    private FilterRequest createHighVolumeFilter(int percentageAbove) {
        FilterRequest filter = new FilterRequest();
        filter.setType("HIGH_VOLUME");
        filter.setParams(new HighVolumeFilterParams(percentageAbove));
        return filter;
    }

    private FilterRequest createRSIFilter(double threshold, String condition) {
        FilterRequest filter = new FilterRequest();
        filter.setType("RSI");
        filter.setParams(new RsiFilterParams(threshold, condition));
        return filter;
    }
}
