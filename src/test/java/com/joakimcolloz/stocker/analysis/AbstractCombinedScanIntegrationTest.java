package com.joakimcolloz.stocker.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterRequest;
import com.joakimcolloz.stocker.analysis.dto.filter.HighVolumeFilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.MacdFilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.MovingAverageFilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.PatternFilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.RsiFilterParams;
import com.joakimcolloz.stocker.api.dao.CandlestickRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base class for combined-scan integration tests.
 * <p>
 * Uses the singleton container pattern so all subclasses share one PostgreSQL
 * instance across the entire test run instead of starting a new container per class.
 * <p>
 * Provides autowired Spring beans and helper methods for creating filter requests.
 */
@SpringBootTest(classes = com.joakimcolloz.stocker.api.Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractCombinedScanIntegrationTest {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:13-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("testdb.sql");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected CandlestickRepository candlestickRepository;

    @Autowired
    protected ObjectMapper objectMapper;

    // Helper methods to create filter requests

    protected FilterRequest createHighVolumeFilter(int percentageAbove) {
        FilterRequest filter = new FilterRequest();
        filter.setType("HIGH_VOLUME");
        filter.setParams(new HighVolumeFilterParams(percentageAbove));
        return filter;
    }

    protected FilterRequest createRSIFilter(double threshold, String condition) {
        FilterRequest filter = new FilterRequest();
        filter.setType("RSI");
        filter.setParams(new RsiFilterParams(threshold, condition));
        return filter;
    }

    protected FilterRequest createMACDFilter(String condition) {
        FilterRequest filter = new FilterRequest();
        filter.setType("MACD");
        filter.setParams(new MacdFilterParams(condition, null, null, null));
        return filter;
    }

    protected FilterRequest createMACDFilter(String condition, int fastPeriod, int slowPeriod, int signalPeriod) {
        FilterRequest filter = new FilterRequest();
        filter.setType("MACD");
        filter.setParams(new MacdFilterParams(condition, fastPeriod, slowPeriod, signalPeriod));
        return filter;
    }

    protected FilterRequest createPatternFilter(String pattern) {
        FilterRequest filter = new FilterRequest();
        filter.setType("PATTERN");
        filter.setParams(new PatternFilterParams(pattern));
        return filter;
    }

    protected FilterRequest createMaCrossoverFilter(int period, String maType, String condition) {
        FilterRequest filter = new FilterRequest();
        filter.setType("MA_CROSSOVER");
        filter.setParams(new MovingAverageFilterParams(period, maType, condition));
        return filter;
    }
}
