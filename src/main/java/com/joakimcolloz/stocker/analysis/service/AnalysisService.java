package com.joakimcolloz.stocker.analysis.service;

import com.joakimcolloz.stocker.analysis.dto.PatternMatch;
import com.joakimcolloz.stocker.analysis.dto.ScanRequest;
import com.joakimcolloz.stocker.api.dao.CandlestickRepository;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
    private final PatternDetectionService patternDetectionService;

    public AnalysisService(CandlestickRepository candlestickRepository,
                           PatternDetectionService patternDetectionService) {
        this.candlestickRepository = candlestickRepository;
        this.patternDetectionService = patternDetectionService;
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
            if ("HAMMER".equalsIgnoreCase(pattern)) {
                allMatches.addAll(patternDetectionService.detectHammer(candlesticks));
            }
            // TODO: Add future patterns here
        }

        // Sort by timestamp descending (most recent first)
        allMatches.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        return allMatches;
    }
}
