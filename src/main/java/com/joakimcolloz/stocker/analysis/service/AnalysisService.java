package com.joakimcolloz.stocker.analysis.service;

import com.joakimcolloz.stocker.analysis.dto.AverageVolumeStatistic;
import com.joakimcolloz.stocker.analysis.dto.HighVolumeCandlestick;
import com.joakimcolloz.stocker.analysis.dto.HighVolumeRequest;
import com.joakimcolloz.stocker.analysis.dto.PatternMatch;
import com.joakimcolloz.stocker.analysis.dto.ScanRequest;
import com.joakimcolloz.stocker.analysis.pattern.PatternRegistry;
import com.joakimcolloz.stocker.api.dao.CandlestickRepository;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service orchestrating pattern analysis across all stocks.
 * <p>
 * Fetches candlestick data from the repository and delegates pattern detection
 * to {@link PatternDetectionService}.
 *
 * @author Joakim Colloz
 * @version 1.0
 */
@Service
public class AnalysisService {

    private final CandlestickRepository candlestickRepository;
    private final PatternRegistry patternRegistry;

    public AnalysisService(CandlestickRepository candlestickRepository,
                           PatternRegistry patternRegistry) {
        this.candlestickRepository = candlestickRepository;
        this.patternRegistry = patternRegistry;
    }

    /**
     * Scans for patterns in all stocks within the specified time range.
     *
     * @param request scan request containing patterns to detect and time range
     * @return list of detected pattern matches
     */
    public List<PatternMatch> scanPatterns(ScanRequest request) {
        // Fetch all candlestick data in time range
        List<Candlestick> candlesticks = candlestickRepository
                .findByTimestampBetweenOrderBySymbolAscTimestampAsc(
                        request.getStartTimestamp(),
                        request.getEndTimestamp()
                );

        List<PatternMatch> allMatches = new ArrayList<>();

        // Run requested pattern detections
        for (String pattern : request.getPatterns()) {
            patternRegistry.findDetector(pattern)
                    .ifPresent(detector -> allMatches.addAll(detector.detect(candlesticks)));
            // TODO: Add future patterns here
        }

        // Sort by timestamp descending (most recent first)
        allMatches.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        return allMatches;
    }

    /**
     * Calculates average trading volume for each stock symbol.
     *
     * @return list of average volume statistics per symbol
     */
    public List<AverageVolumeStatistic> calculateAverageVolumes(long startTimestamp, long endTimestamp) {
        List<Object[]> results = candlestickRepository.findAverageVolumeBySymbolAndTimeRange(
                startTimestamp, endTimestamp
        );

        List<AverageVolumeStatistic> statistics = new ArrayList<>();

        for (Object[] result : results) {
            String symbol = (String) result[0];
            // AVG() in JPQL returns Double, convert to long
            long averageVolume = ((Number) result[1]).longValue();
            int dataPoints = ((Number) result[2]).intValue();

            statistics.add(new AverageVolumeStatistic(symbol, averageVolume, dataPoints));
        }

        return statistics;
    }

    /**
     * Identifies candlesticks with volume above average by specified percentage.
     *
     * @param request request containing time range and percentage threshold
     * @return list of high volume candlesticks
     */
    public List<HighVolumeCandlestick> findHighVolumeCandlesticks(HighVolumeRequest request) {
        // Step 1: Calculate average volume per symbol for the time range
        List<Object[]> avgResults = candlestickRepository.findAverageVolumeBySymbolAndTimeRange(
                request.getStartTimestamp(),
                request.getEndTimestamp()
        );

        // Store averages in map for quick lookup
        Map<String, Long> averageVolumeBySymbol = new HashMap<>();
        for (Object[] result : avgResults) {
            String symbol = (String) result[0];
            long avgVolume = ((Number) result[1]).longValue();
            averageVolumeBySymbol.put(symbol, avgVolume);
        }

        // Step 2: Fetch all candlesticks in time range
        List<Candlestick> candlesticks = candlestickRepository
                .findByTimestampBetweenOrderBySymbolAscTimestampAsc(
                        request.getStartTimestamp(),
                        request.getEndTimestamp()
                );

        // Step 3: Find candlesticks above threshold
        List<HighVolumeCandlestick> highVolumeCandlesticks = new ArrayList<>();

        for (Candlestick candle : candlesticks) {
            Long avgVolume = averageVolumeBySymbol.get(candle.getSymbol());

            if (avgVolume == null) continue;

            // Calculate threshold: average * (1 + percentage/100)
            double threshold = avgVolume * (1.0 + request.getPercentageAbove() / 100.0);

            if (candle.getVolume() > threshold) {
                // Calculate actual percentage above average
                int actualPercentage = (int) (((candle.getVolume() - avgVolume) * 100.0) / avgVolume);

                highVolumeCandlesticks.add(new HighVolumeCandlestick(
                        candle.getSymbol(),
                        candle.getTimestamp(),
                        candle.getVolume(),
                        avgVolume,
                        actualPercentage
                ));
            }
        }

        // Sort by timestamp descending (most recent first)
        highVolumeCandlesticks.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        return highVolumeCandlesticks;
    }

    public List<String> findAllDistinctSymbols() {
        return candlestickRepository.findAllDistinctSymbols();
    }

    public List<Candlestick> findBySymbolAndTimestampBetween(String symbol, long startTimestamp, long endTimestamp) {
        return candlestickRepository.findBySymbolAndTimestampBetween(symbol, startTimestamp, endTimestamp);
    }
}
