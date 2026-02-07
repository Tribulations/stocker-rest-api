package com.joakimcolloz.stocker.analysis;

import com.joakimcolloz.stocker.analysis.dto.request.CombinedScanRequest;
import com.joakimcolloz.stocker.analysis.dto.response.CombinedFilterResult;
import com.joakimcolloz.stocker.analysis.indicator.RsiCalculator;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.joakimcolloz.stocker.api.AnalysisApiIntegrationTestUtil.humanReadableToTimestamp;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying RSI consistency between the combined-scan endpoint
 * and the stable RSI calculator.
 */
@DisplayName("RSI Consistency Integration Tests")
public class RsiScanIntegrationTest extends AbstractCombinedScanIntegrationTest {

    @Test
    @DisplayName("Should produce RSI values matching stable calculator when lookback data exists")
    void testRsiMatchesStableCalculator() throws Exception {
        candlestickRepository.deleteAll();

        // Insert 35 days of data for a single symbol with a clear uptrend
        long baseTimestamp = humanReadableToTimestamp("2024-01-01 09:00:00");
        List<Candlestick> testData = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            Candlestick c = new Candlestick();
            c.setSymbol("TEST.ST");
            c.setTimestamp(baseTimestamp + (i * 86400L));
            double price = 100.0 + (i % 3 == 0 ? -1.0 : 2.0) * (i + 1) / 5.0;
            c.setOpen(price);
            c.setHigh(price + 2.0);
            c.setLow(price - 1.0);
            c.setClose(price + 1.0);
            c.setVolume(100000L);
            testData.add(c);
        }
        candlestickRepository.saveAll(testData);

        // Request only the last 7 days (days 29-35) with RSI filter
        // The lookback extension should pull in 28 days of extra history
        long windowStart = baseTimestamp + (28 * 86400L);
        long windowEnd = baseTimestamp + (34 * 86400L);

        CombinedScanRequest request = new CombinedScanRequest();
        request.setFilters(List.of(createRSIFilter(0, "above")));
        request.setStartTimestamp(windowStart);
        request.setEndTimestamp(windowEnd);

        MvcResult mvcResult = mockMvc.perform(post("/api/analysis/combined-scan")
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andReturn();

        CombinedFilterResult[] results = objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(),
                CombinedFilterResult[].class
        );
        assertFalse(results.length == 0, "Expected at least one RSI match");

        // Compute expected RSI values using the stable calculator with all 35 days of data
        List<Candlestick> allSorted = testData.stream()
                .sorted(Comparator.comparingLong(Candlestick::getTimestamp))
                .toList();
        Map<Long, Double> expectedRsi = RsiCalculator.calculateStableRsiByTimestamp(allSorted, 14);

        for (CombinedFilterResult result : results) {
            Double expected = expectedRsi.get(result.getTimestamp());
            assertFalse(expected == null, "Expected RSI value for timestamp " + result.getTimestamp());
            double actual = result.getFilters().get(0).getValues().get("rsi");
            assertEquals(expected, actual, 0.01,
                    "RSI at timestamp " + result.getTimestamp() + " should match stable calculator");
        }
    }
}
