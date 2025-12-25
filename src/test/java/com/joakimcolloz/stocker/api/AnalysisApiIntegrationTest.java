package com.joakimcolloz.stocker.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joakimcolloz.stocker.analysis.dto.ScanRequest;
import com.joakimcolloz.stocker.analysis.dto.ScanResponse;
import com.joakimcolloz.stocker.api.dao.CandlestickRepository;
import com.joakimcolloz.stocker.api.entity.Candlestick;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

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
 * </ul>
 *
 * @author Joakim Colloz
 * @version 1.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@DisplayName("Analysis API Integration Tests")
public class AnalysisApiIntegrationTest {

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

    /**
     * Sets up test data with various candlestick patterns.
     */
    @BeforeEach
    void setUp() {
        candlestickRepository.deleteAll();

        // Regular candle (not a hammer)
        candlestickRepository.save(new Candlestick(
                100, 102, 99, 103, 5000, 1753038000L, "BOL.ST"));

        // Hammer pattern: long lower shadow, small body, tiny upper shadow
        // Body = |Close - Open| = |100 - 98| = 2
        // Lower shadow = min(Open, Close) - Low = 98 - 90 = 8
        // Upper shadow = High - max(Open, Close) = 100.5 - 100 = 0.5
        candlestickRepository.save(new Candlestick(
                98, 100, 90, 100.5, 6000, 1753124400L, "BOL.ST"));

        // Another hammer for different symbol
        candlestickRepository.save(new Candlestick(
                95, 97, 85, 97.5, 7000, 1753210800L, "ABB.ST"));

        // Not a hammer (balanced shadows)
        candlestickRepository.save(new Candlestick(
                100, 105, 95, 110, 8000, 1753297200L, "BOL.ST"));
    }

    /**
     * Tests for API key authentication on analysis endpoints.
     */
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

            mockMvc.perform(post("/api/analysis/scan")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
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
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    /**
     * Tests for the supported patterns endpoint.
     */
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

    /**
     * Tests for the pattern scanning endpoint validation.
     */
    @Nested
    @DisplayName("Scan Endpoint Validation Tests")
    class ScanValidationTests {

        @Test
        @DisplayName("Should return 400 when patterns list is empty")
        void testScanWithEmptyPatterns() throws Exception {
            ScanRequest request = new ScanRequest();
            request.setPatterns(Set.of());
            request.setStartTimestamp(1753038000L);
            request.setEndTimestamp(1753297200L);

            mockMvc.perform(post("/api/analysis/scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when start timestamp is 0")
        void testScanWithInvalidStartTimestamp() throws Exception {
            ScanRequest request = new ScanRequest();
            request.setPatterns(Set.of("HAMMER"));
            request.setStartTimestamp(0);
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
            request.setStartTimestamp(1753297200L);
            request.setEndTimestamp(1753038000L);

            mockMvc.perform(post("/api/analysis/scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    /**
     * Tests for hammer pattern detection functionality.
     */
    @Nested
    @DisplayName("Hammer Pattern Detection Tests")
    class HammerDetectionTests {

        @Test
        @DisplayName("Should detect hammer patterns in test data")
        void testDetectHammerPatterns() throws Exception {
            ScanRequest request = new ScanRequest();
            request.setPatterns(Set.of("HAMMER"));
            request.setStartTimestamp(1753038000L);
            request.setEndTimestamp(1753297200L);

            mockMvc.perform(post("/api/analysis/scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalMatches").value(greaterThanOrEqualTo(2)))
                    .andExpect(jsonPath("$.message").value("Patterns found"))
                    .andExpect(jsonPath("$.matches", hasSize(greaterThanOrEqualTo(2))))
                    .andExpect(jsonPath("$.matches[*].patternType", everyItem(is("HAMMER"))))
                    .andExpect(jsonPath("$.matches[*].symbol", hasItem("ABB.ST")))
                    .andExpect(jsonPath("$.matches[*].symbol", hasItem("BOL.ST")));
        }

        @Test
        @DisplayName("Should include confidence scores in hammer matches")
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
            request.setEndTimestamp(1753038001L);

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

            String response = mockMvc.perform(post("/api/analysis/scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            var scanResponse = objectMapper.readValue(response, ScanResponse.class);

            // Verify descending order
            for (int i = 0; i < scanResponse.getMatches().size() - 1; i++) {
                assertTrue(scanResponse.getMatches().get(i).getTimestamp() >=
                                scanResponse.getMatches().get(i + 1).getTimestamp(),
                        "Results should be sorted by timestamp descending");
            }
        }
    }

    /**
     * Tests for edge cases and boundary conditions.
     */
    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty database gracefully")
        void testScanEmptyDatabase() throws Exception {
            candlestickRepository.deleteAll();

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
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalMatches").value(0));
        }
    }
}
