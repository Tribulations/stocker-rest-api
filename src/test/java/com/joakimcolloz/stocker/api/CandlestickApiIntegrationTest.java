package com.joakimcolloz.stocker.api;

import com.joakimcolloz.stocker.api.dao.CandlestickRepository;
import com.joakimcolloz.stocker.api.entity.Candlestick;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import org.testcontainers.containers.PostgreSQLContainer;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@DisplayName("Candlestick REST API Integration Tests")
public class CandlestickApiIntegrationTest {
    /**
     * PostgreSQL container managed by Testcontainers for integration testing.
     * Initializes schema and data using the {@code testdb.sql} script located in the test resources directory.
     */
    @Container
    public static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:13-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("testdb.sql");

    /**
     * Dynamically sets Spring datasource properties using the Testcontainer PostgreSQL instance.
     *
     * @param registry property registry for the Spring test context
     */
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

    /**
     * Clears and inserts test candlestick data into the database before each test.
     */
    @BeforeEach
    void setUp() {
        candlestickRepository.deleteAll();
        candlestickRepository.save(new Candlestick(100, 102, 113, 97, 5000,
                1753038000L, "BOL.ST"));
        candlestickRepository.save(new Candlestick(102, 104, 115, 99, 6000,
                1753124400L, "BOL.ST"));
    }

    @Test
    @DisplayName("Should return 401 when no API key provided")
    void testUnauthorizedWithoutApiKey() throws Exception {
        mockMvc.perform(get("/api/candlesticks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 200 when valid API key provided")
    void testAuthorizedWithValidApiKey() throws Exception {
        mockMvc.perform(get("/api/candlesticks")
                .header("X-API-Key", "test-api-key"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return 401 when invalid API key provided")
    void testUnauthorizedWithInvalidApiKey() throws Exception {
        mockMvc.perform(get("/api/candlesticks")
                .header("X-API-Key", "invalid-key"))
                .andExpect(status().isUnauthorized());
    }
}
