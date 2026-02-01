package com.joakimcolloz.stocker.api;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.joakimcolloz.stocker.analysis.dto.request.HighVolumeRequest;
import com.joakimcolloz.stocker.analysis.dto.request.ScanRequest;
import com.joakimcolloz.stocker.analysis.dto.response.ScanResponse;
import com.joakimcolloz.stocker.analysis.dto.statistics.HighVolumeCandlestick;
import com.joakimcolloz.stocker.api.dao.CandlestickRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

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

import java.util.List;
import java.util.Set;

import static com.joakimcolloz.stocker.api.AnalysisApiIntegrationTestUtil.humanReadableToTimestamp;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Integration tests for the {@code /api/analysis} REST API endpoints.
 * <p>
 * Tests pattern detection functionality including:
 * <ul>
 *   <li>API key authentication</li>
 *   <li>Pattern scanning endpoint</li>
 *   <li>Supported patterns endpoint</li>
 *   <li>Hammer pattern detection logic</li>
 *   <li>Average volume calculation logic</li>
 *   <li>High volume detection</li>
 * </ul>
 *
 * @author Joakim Colloz
 * @version 1.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Analysis API Integration Tests")
public class AnalysisApiIntegrationTest {
    @Container
    @SuppressWarnings("resource")
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

    @Nested
    @DisplayName("API Key Authentication Tests")
    class ApiKeyAuthenticationTests {
        @Test
        @DisplayName("Should return 401 when scanning without API key")
        void testScanUnauthorizedWithoutApiKey() throws Exception {
            ScanRequest request = new ScanRequest();
            request.setPatterns(Set.of("HAMMER"));
            request.setStartTimestamp(1753038000L);
            request.setEndTimestamp(1753297200L);

            // MockMvc.perform: Simulates HTTP POST request to endpoint
            mockMvc.perform(post("/api/analysis/scan")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized()); // Expect HTTP 401
        }

        @Test
        @DisplayName("Should return 401 when getting supported patterns without API key")
        void testGetSupportedPatternsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/analysis/patterns/supported"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 200 when scanning with valid API key")
        void testScanAuthorizedWithValidApiKey() throws Exception {
            ScanRequest request = new ScanRequest();
            request.setPatterns(Set.of("HAMMER"));
            request.setStartTimestamp(1753038000L);
            request.setEndTimestamp(1753297200L);

            mockMvc.perform(post("/api/analysis/scan")
                            // .header: Adds HTTP header to simulate authenticated request
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk()); // HTTP 200
        }
    }

    @Nested
    @DisplayName("Supported Patterns Endpoint Tests")
    class SupportedPatternsTests {
        @Test
        @DisplayName("Should return list of supported patterns")
        void testGetSupportedPatterns() throws Exception {
            mockMvc.perform(get("/api/analysis/patterns/supported")
                            .header("X-API-Key", "test-api-key"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                    .andExpect(jsonPath("$[0].code").value("HAMMER"))
                    .andExpect(jsonPath("$[0].name").value("Hammer"))
                    .andExpect(jsonPath("$[0].type").value("BULLISH"))
                    .andExpect(jsonPath("$[0].description").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("Scan Endpoint Validation Tests")
    class ScanValidationTests {
        @Test
        @DisplayName("Should return 400 when patterns list is empty")
        void testScanWithEmptyPatterns() throws Exception {
            ScanRequest request = new ScanRequest();
            request.setPatterns(Set.of()); // Invalid: empty patterns
            request.setStartTimestamp(humanReadableToTimestamp("2025-07-20 09:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2025-07-23 09:00:00"));

            mockMvc.perform(post("/api/analysis/scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest()); // HTTP 400
        }

        @Test
        @DisplayName("Should return 400 when start timestamp is 0")
        void testScanWithInvalidStartTimestamp() throws Exception {
            ScanRequest request = new ScanRequest();
            request.setPatterns(Set.of("HAMMER"));
            request.setStartTimestamp(0); // Invalid timestamp
            request.setEndTimestamp(1753297200L);

            mockMvc.perform(post("/api/analysis/scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when end timestamp is before start")
        void testScanWithInvalidTimeRange() throws Exception {
            ScanRequest request = new ScanRequest();
            request.setPatterns(Set.of("HAMMER"));
            request.setStartTimestamp(1753297200L); // End before start
            request.setEndTimestamp(1753038000L);

            mockMvc.perform(post("/api/analysis/scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {
        @Test
        @DisplayName("Should handle empty database gracefully")
        void testScanEmptyDatabase() throws Exception {
            candlestickRepository.deleteAll(); // Override @BeforeEach setup

            ScanRequest request = new ScanRequest();
            request.setPatterns(Set.of("HAMMER"));
            request.setStartTimestamp(1753038000L);
            request.setEndTimestamp(1753297200L);

            mockMvc.perform(post("/api/analysis/scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalMatches").value(0))
                    .andExpect(jsonPath("$.matches", hasSize(0)));
        }

        @Test
        @DisplayName("Should handle unknown pattern type gracefully")
        void testScanWithUnknownPattern() throws Exception {
            ScanRequest request = new ScanRequest();
            request.setPatterns(Set.of("UNKNOWN_PATTERN"));
            request.setStartTimestamp(1753038000L);
            request.setEndTimestamp(1753297200L);

            mockMvc.perform(post("/api/analysis/scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk()) // No error, just no results
                    .andExpect(jsonPath("$.totalMatches").value(0));
        }
    }

    @Nested
    @DisplayName("Hammer Pattern Detection Tests")
    class HammerDetectionTests {
        @BeforeEach
        void setUpHammerTestData() {
            candlestickRepository.deleteAll();
            AnalysisApiIntegrationTestUtil.insertHammerTestDataToDb(candlestickRepository);
        }

        @Test
        @DisplayName("Should detect hammer patterns in test data")
        void testDetectHammerPatterns() throws Exception {
            ScanRequest request = new ScanRequest();
            request.setPatterns(Set.of("HAMMER"));
            request.setStartTimestamp(humanReadableToTimestamp("2025-07-19 09:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2025-07-25 09:00:00"));

            mockMvc.perform(post("/api/analysis/scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    // Verify response structure and data correctness
                    .andExpect(jsonPath("$.totalMatches").value(greaterThanOrEqualTo(2)))
                    .andExpect(jsonPath("$.message").value("Patterns found"))
                    .andExpect(jsonPath("$.matches", hasSize(greaterThanOrEqualTo(2))))
                    // everyItem: All elements in array must match
                    .andExpect(jsonPath("$.matches[*].patternType", everyItem(is("HAMMER"))))
                    // hasItem: At least one element matches
                    .andExpect(jsonPath("$.matches[*].symbol", hasItem("ABB.ST")))
                    .andExpect(jsonPath("$.matches[*].symbol", hasItem("BOL.ST")));
        }

        @Test
        @DisplayName("Should include confidence scores (within valid bounds) in hammer matches")
        void testHammerConfidenceScores() throws Exception {
            ScanRequest request = new ScanRequest();
            request.setPatterns(Set.of("HAMMER"));
            request.setStartTimestamp(1753038000L);
            request.setEndTimestamp(1753297200L);

            mockMvc.perform(post("/api/analysis/scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    // Validate confidence score is a valid percentage
                    .andExpect(jsonPath("$.matches[0].confidence", greaterThan(0)))
                    .andExpect(jsonPath("$.matches[0].confidence", lessThanOrEqualTo(99)))
                    .andExpect(jsonPath("$.matches[0].description").value("Bullish reversal pattern"))
                    .andExpect(jsonPath("$.matches[0].timestamp").isNumber());
        }

        @Test
        @DisplayName("Should return no matches when no hammers in range")
        void testNoHammersDetected() throws Exception {
            // Query a time range with only the first candle (not a hammer)
            ScanRequest request = new ScanRequest();
            request.setPatterns(Set.of("HAMMER"));
            request.setStartTimestamp(1753038000L);
            request.setEndTimestamp(1753038001L); // Narrow range excludes hammers

            mockMvc.perform(post("/api/analysis/scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalMatches").value(0))
                    .andExpect(jsonPath("$.message").value("No patterns detected"))
                    .andExpect(jsonPath("$.matches", hasSize(0)));
        }

        @Test
        @DisplayName("Should sort results by timestamp descending")
        void testResultsSortedByTimestamp() throws Exception {
            ScanRequest request = new ScanRequest();
            request.setPatterns(Set.of("HAMMER"));
            request.setStartTimestamp(1753038000L);
            request.setEndTimestamp(1753297200L);

            // andReturn: Captures full response for custom assertions
            String response = mockMvc.perform(post("/api/analysis/scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // Deserialize JSON to Plain Old Java Object (POJO) for complex validation
            var scanResponse = objectMapper.readValue(response, ScanResponse.class);

            // Verify descending order
            // Manual assertion as jsonPath matchers are insufficient
            for (int i = 0; i < scanResponse.getMatches().size() - 1; i++) {
                assertTrue(scanResponse.getMatches().get(i).getTimestamp() >=
                                scanResponse.getMatches().get(i + 1).getTimestamp(),
                        "Results should be sorted by timestamp descending");
            }
        }
    }

    @Nested
    @DisplayName("Average Volume Statistics Tests")
    class AverageVolumeStatisticsTests {

        @BeforeEach
        void setUpVolumeTestData() {
            candlestickRepository.deleteAll();
            AnalysisApiIntegrationTestUtil.insertAverageVolumeTestDataToDb(candlestickRepository);
        }

        @Test
        @DisplayName("Should calculate average volume for each symbol")
        void testCalculateAverageVolumes() throws Exception {
            long start = humanReadableToTimestamp("2025-12-01 00:00:00");
            long end = humanReadableToTimestamp("2025-12-05 23:59:59");

            mockMvc.perform(get("/api/analysis/statistics/average-volume")
                            .param("startTimestamp", String.valueOf(start))
                            .param("endTimestamp", String.valueOf(end))
                            .header("X-API-Key", "test-api-key"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].symbol").value("ABB.ST"))
                    .andExpect(jsonPath("$[0].averageVolume").value(6000))
                    .andExpect(jsonPath("$[0].dataPoints").value(2))
                    .andExpect(jsonPath("$[1].symbol").value("BOL.ST"))
                    .andExpect(jsonPath("$[1].averageVolume").value(2000))
                    .andExpect(jsonPath("$[1].dataPoints").value(3));
        }

        @Test
        @DisplayName("Should return empty list when no data in range")
        void testAverageVolumesEmptyDatabase() throws Exception {
            long start = humanReadableToTimestamp("2025-12-01 00:00:00");
            long end = humanReadableToTimestamp("2025-12-05 23:59:59");

            candlestickRepository.deleteAll();

            mockMvc.perform(get("/api/analysis/statistics/average-volume")
                            .param("startTimestamp", String.valueOf(start))
                            .param("endTimestamp", String.valueOf(end))
                            .header("X-API-Key", "test-api-key"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Should return 400 when timestamps are invalid")
        void testAverageVolumesInvalidTimestamps() throws Exception {
            mockMvc.perform(get("/api/analysis/statistics/average-volume")
                            .param("startTimestamp", "0")
                            .param("endTimestamp", "1000")
                            .header("X-API-Key", "test-api-key"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("High Volume Detection Tests")
    class HighVolumeDetectionTests {

        @BeforeEach
        void setUpHighVolumeTestData() {
            candlestickRepository.deleteAll();
            AnalysisApiIntegrationTestUtil.insertHighVolumeTestDataToDb(candlestickRepository);
        }

        @Test
        @DisplayName("Should detect candlesticks with volume 50% above average")
        void testDetectHighVolume50Percent() throws Exception {
            HighVolumeRequest request = new HighVolumeRequest();
            request.setStartTimestamp(humanReadableToTimestamp("2025-12-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2025-12-06 23:59:59"));
            request.setPercentageAbove(50);

            mockMvc.perform(post("/api/analysis/high-volume")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].symbol").value("BOL.ST"))
                    .andExpect(jsonPath("$[0].volume").value(6000))
                    .andExpect(jsonPath("$[0].averageVolume").value(3000))
                    .andExpect(jsonPath("$[0].percentageAboveAverage").value(100));
        }

        @Test
        @DisplayName("Should detect multiple symbols with high volume")
        void testDetectHighVolumeMultipleSymbols() throws Exception {
            HighVolumeRequest request = new HighVolumeRequest();
            request.setStartTimestamp(humanReadableToTimestamp("2025-12-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2025-12-06 23:59:59"));
            request.setPercentageAbove(30);

            mockMvc.perform(post("/api/analysis/high-volume")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].symbol").value("ABB.ST"))
                    .andExpect(jsonPath("$[1].symbol").value("BOL.ST"));
        }

        @Test
        @DisplayName("Should return empty list when no high volume candlesticks")
        void testNoHighVolumeDetected() throws Exception {
            HighVolumeRequest request = new HighVolumeRequest();
            request.setStartTimestamp(humanReadableToTimestamp("2025-12-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2025-12-06 23:59:59"));
            request.setPercentageAbove(200); // Very high threshold

            mockMvc.perform(post("/api/analysis/high-volume")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Should return 400 when timestamps are invalid")
        void testHighVolumeInvalidTimestamps() throws Exception {
            HighVolumeRequest request = new HighVolumeRequest();
            request.setStartTimestamp(0);
            request.setEndTimestamp(1000);
            request.setPercentageAbove(50);

            mockMvc.perform(post("/api/analysis/high-volume")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when percentage is negative")
        void testHighVolumeNegativePercentage() throws Exception {
            HighVolumeRequest request = new HighVolumeRequest();
            request.setStartTimestamp(humanReadableToTimestamp("2025-12-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2025-12-06 23:59:59"));
            request.setPercentageAbove(-10);

            mockMvc.perform(post("/api/analysis/high-volume")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should not include candlesticks below threshold")
        void testExcludesBelowThreshold() throws Exception {
            HighVolumeRequest request = new HighVolumeRequest();
            request.setStartTimestamp(humanReadableToTimestamp("2025-12-01 00:00:00"));
            request.setEndTimestamp(humanReadableToTimestamp("2025-12-06 23:59:59"));
            request.setPercentageAbove(50);

            String response = mockMvc.perform(post("/api/analysis/high-volume")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            var results = objectMapper.readValue(response,
                    new com.fasterxml.jackson.core.type.TypeReference<List<HighVolumeCandlestick>>() {});

            // Verify these specific candlesticks are NOT included (below 50% threshold)
            assertTrue(results.stream().noneMatch(c ->
                            c.getVolume() == 2000 && c.getSymbol().equals("BOL.ST")),
                    "Should not include BOL.ST with volume 2000");

            assertTrue(results.stream().noneMatch(c ->
                            c.getVolume() == 3000 && c.getSymbol().equals("BOL.ST")),
                    "Should not include BOL.ST with volume 3000");
        }
    }
}
