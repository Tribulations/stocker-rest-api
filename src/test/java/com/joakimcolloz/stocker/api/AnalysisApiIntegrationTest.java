package com.joakimcolloz.stocker.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joakimcolloz.stocker.analysis.dto.ScanRequest;
import com.joakimcolloz.stocker.analysis.dto.ScanResponse;
import com.joakimcolloz.stocker.api.dao.CandlestickRepository;
import com.joakimcolloz.stocker.api.entity.Candlestick;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

// Spring dependency injection for test beans
import org.springframework.beans.factory.annotation.Autowired;
// Autoconfigures MockMvc bean for MVC layer testing
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
// Loads full Spring application context for integration tests
import org.springframework.boot.test.context.SpringBootTest;
// HTTP content type constants (APPLICATION_JSON, etc.)
import org.springframework.http.MediaType;
// Activates specific Spring profiles for testing
import org.springframework.test.context.ActiveProfiles;
// Registry for adding properties at runtime (used with Testcontainers)
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;

// Hamcrest matchers - third-party assertion library for readable, expressive test conditions
// Used with MockMvc's jsonPath() to validate JSON responses in a fluent way
// 'static import' allows calling matchers directly: hasSize(2) instead of Matchers.hasSize(2)
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
 * </ul>
 *
 * @author Joakim Colloz
 * @version 1.0
 */
// @SpringBootTest: Loads the FULL Spring application context (all beans, configurations, auto-configurations)
// - Creates a real Spring environment, not just isolated components
// - Use for integration tests that need multiple layers working together (controllers, services, repositories)
// - Slower than @WebMvcTest or @DataJpaTest but tests realistic application behavior
@SpringBootTest

// @AutoConfigureMockMvc: Provides MockMvc bean for testing MVC layer WITHOUT starting an actual HTTP server
// - MockMvc simulates HTTP requests in-memory (faster than real server, no network ports)
// - Alternative to @SpringBootTest(webEnvironment = RANDOM_PORT) which starts a real embedded server
// - Gives you full Spring MVC stack (filters, interceptors, exception handlers) but no network overhead
@AutoConfigureMockMvc

// @Testcontainers: Activates JUnit 5 extension that manages Docker container lifecycle
// - Automatically starts containers BEFORE tests run and stops them AFTER tests complete
// - Works with @Container fields to spin up databases, message queues, etc.
// - Ensures tests run against real dependencies (PostgreSQL here) instead of in-memory H2 or mocks
@Testcontainers

// @ActiveProfiles("test"): Tells Spring to load configuration from application-test.yml/properties
// - Overrides default application.yml settings with test-specific values
// - Commonly used for: test database URLs, disabled security, mock external services
// - Properties precedence: application-test.yml > application.yml > @DynamicPropertySource
@ActiveProfiles("test")

// @ExtendWith(SpringExtension.class): Tells JUnit to use Spring's test extension
// - Integrates Spring's testing features with JUnit 5
// - Enables @Autowired, @MockBean, @TestPropertySource, etc.
// - Required for @SpringBootTest and @WebMvcTest
//@ExtendWith(SpringExtension.class)
@DisplayName("Analysis API Integration Tests")
public class AnalysisApiIntegrationTest {

    // @Container: Testcontainers manages this PostgreSQL instance lifecycle
    // Shared across all tests in this class (static field)
    @Container
    public static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:13-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("testdb.sql");

    // @DynamicPropertySource: Injects Testcontainers DB connection into Spring context at runtime
    // - Runs BEFORE Spring context loads, overriding properties from application.properties/application.yml
    // - application.yml and application.properties are Spring Boot config files (YAML vs properties format)
    // - Needed because Testcontainers assigns random ports - we don't know DB URL until container starts
    // - Highest precedence: @DynamicPropertySource > application-test.properties > application.properties
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
    }

    // @Autowired: Spring injects MockMvc bean to simulate HTTP requests without network calls
    // MockMvc is Spring's test utility that:
    // - Calls controllers through Spring's DispatcherServlet (same entry point as real HTTP)
    // - Processes requests through the FULL MVC pipeline IN-MEMORY (no socket, no TCP/IP, no server startup)
    // - Tests complete stack: routing, filters, interceptors, validation, serialization, exception handlers
    // - Example: mockMvc.perform(post("/api/scan")) → Spring routes to @PostMapping → returns testable result
    // Value: Catches integration issues (JSON mapping, security, validation) faster than real HTTP, more realistic than unit tests
    @Autowired
    private MockMvc mockMvc;

    // Real repository bean from Spring context - not mocked for integration testing
    @Autowired
    private CandlestickRepository candlestickRepository;

    // ObjectMapper for JSON serialization/deserialization in tests
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Sets up test data with various candlestick patterns.
     */
    // @BeforeEach: Runs before each test method to ensure clean, isolated test data
    @BeforeEach
    void setUp() {
        // Start fresh: delete all data to prevent test pollution
        candlestickRepository.deleteAll();

        // Regular candle (not a hammer)
        candlestickRepository.save(new Candlestick(
                100, 102, 99, 103, 5000,
                humanReadableToTimestamp("2025-07-20 09:00:00"), "BOL.ST"));

        // Hammer pattern: long lower shadow, small body, tiny upper shadow
        // Body = |Close - Open| = |100 - 98| = 2
        // Lower shadow = min(Open, Close) - Low = 98 - 90 = 8
        // Upper shadow = High - max(Open, Close) = 100.5 - 100 = 0.5
        candlestickRepository.save(new Candlestick(
                98, 100, 90, 100.5, 6000,
                humanReadableToTimestamp("2025-07-21 09:00:00"), "BOL.ST"));

        // Another hammer for different symbol
        candlestickRepository.save(new Candlestick(
                95, 97, 85, 97.5, 7000,
                humanReadableToTimestamp("2025-07-20 09:00:00"), "ABB.ST"));

        // Not a hammer (balanced shadows)
        candlestickRepository.save(new Candlestick(
                100, 105, 95, 110, 8000,
                humanReadableToTimestamp("2025-07-22 09:00:00"), "BOL.ST"));
    }

    /**
     * Tests for API key authentication on analysis endpoints.
     */
    // @Nested: Groups related tests for better organization and readability
    // Each nested class can have its own @BeforeEach setup
    @Nested
    @DisplayName("API Key Authentication Tests")
    class ApiKeyAuthenticationTests {

        // Intent: Verify endpoint security by ensuring unauthorized requests are rejected
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
                    // andExpect: Fluent API for asserting response properties
                    // Fluent API = design pattern (not Spring-specific) where methods return 'this' for chaining
                    // Fluent API is a pattern, Spring implements it
                    // Spring's MockMvc uses this pattern: .perform().andExpect().andExpect()
                    // Other examples: Java's Stream API, StringBuilder, Mockito
                    // Benefit: Readable, self-documenting test code
                    .andExpect(status().isUnauthorized()); // Expect HTTP 401
        }

        @Test
        @DisplayName("Should return 401 when getting supported patterns without API key")
        void testGetSupportedPatternsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/analysis/patterns/supported"))
                    .andExpect(status().isUnauthorized());
        }

        // Intent: Verify valid API key grants access (positive test case)
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

    /**
     * Tests for the supported patterns endpoint.
     */
    @Nested
    @DisplayName("Supported Patterns Endpoint Tests")
    class SupportedPatternsTests {

        // Intent: Verify API returns available pattern metadata for client usage
        @Test
        @DisplayName("Should return list of supported patterns")
        void testGetSupportedPatterns() throws Exception {
            mockMvc.perform(get("/api/analysis/patterns/supported")
                            .header("X-API-Key", "test-api-key"))
                    .andExpect(status().isOk())
                    // jsonPath: Hamcrest matchers for JSON response validation
                    // $: JSON root, [0]: first array element
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

        // Intent: Ensure business rules are enforced (patterns list cannot be empty)
        @Test
        @DisplayName("Should return 400 when patterns list is empty")
        void testScanWithEmptyPatterns() throws Exception {
            ScanRequest request = new ScanRequest();
            request.setPatterns(Set.of()); // Invalid: empty patterns
            request.setStartTimestamp(1753038000L);
            request.setEndTimestamp(1753297200L);

            mockMvc.perform(post("/api/analysis/scan")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest()); // HTTP 400
        }

        // Intent: Verify timestamp validation prevents invalid queries
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

        // Intent: Validate time range logic (end must be after start)
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

    /**
     * Tests for hammer pattern detection functionality.
     */
    @Nested
    @DisplayName("Hammer Pattern Detection Tests")
    class HammerDetectionTests {

        // Intent: Verify core business logic detects hammer patterns from test data
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

        // Intent: Ensure confidence scores are calculated and within valid bounds
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
                    // Validate confidence score is a valid percentage
                    .andExpect(jsonPath("$.matches[0].confidence", greaterThan(0)))
                    .andExpect(jsonPath("$.matches[0].confidence", lessThanOrEqualTo(99)))
                    .andExpect(jsonPath("$.matches[0].description").value("Bullish reversal pattern"))
                    .andExpect(jsonPath("$.matches[0].timestamp").isNumber());
        }

        // Intent: Test negative case - no matches should return empty results gracefully
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

        // Intent: Verify results are ordered correctly for client display
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

            // Deserialize JSON to POJO for complex validation
            var scanResponse = objectMapper.readValue(response, ScanResponse.class);

            // Verify descending order
            // Manual assertion when jsonPath matchers are insufficient
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

        // Intent: Verify system handles empty data set without errors
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

        // Intent: Ensure unknown patterns don't cause errors (graceful degradation)
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
    @DisplayName("Average Volume Statistics Tests")
    class AverageVolumeStatisticsTests {

        @BeforeEach
        void setUpVolumeTestData() {
            candlestickRepository.deleteAll();

            // BOL.ST - volumes: 1000, 2000, 3000 (avg = 2000)
            candlestickRepository.save(new Candlestick(
                    100, 102, 99, 103, 1000,
                    humanReadableToTimestamp("2025-12-01 09:00:00"), "BOL.ST"));

            candlestickRepository.save(new Candlestick(
                    100, 102, 99, 103, 2000,
                    humanReadableToTimestamp("2025-12-02 09:00:00"), "BOL.ST"));

            candlestickRepository.save(new Candlestick(
                    100, 102, 99, 103, 3000,
                    humanReadableToTimestamp("2025-12-03 09:00:00"), "BOL.ST"));

            // ABB.ST - volumes: 5000, 7000 (avg = 6000)
            candlestickRepository.save(new Candlestick(
                    100, 102, 99, 103, 5000,
                    humanReadableToTimestamp("2025-12-04 09:00:00"), "ABB.ST"));

            candlestickRepository.save(new Candlestick(
                    100, 102, 99, 103, 7000,
                    humanReadableToTimestamp("2025-12-05 09:00:00"), "ABB.ST"));
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

    private long humanReadableToTimestamp(String humanReadableTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime dateTime = LocalDateTime.parse(humanReadableTime, formatter);
        return dateTime.atZone(ZoneId.systemDefault()).toEpochSecond();
    }
}
