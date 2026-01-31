package com.joakimcolloz.stocker.analysis.service;

import com.joakimcolloz.stocker.api.dao.CandlestickRepository;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import com.joakimcolloz.stocker.analysis.dto.CombinedScanRequest;
import com.joakimcolloz.stocker.analysis.dto.FilterMatch;
import com.joakimcolloz.stocker.analysis.dto.FilterRequest;
import com.joakimcolloz.stocker.analysis.filter.FilterRegistry;
import com.joakimcolloz.stocker.analysis.filter.FilterStrategy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for executing combined filter scans.
 */
@Service
public class CombinedScanService {

    private final CandlestickRepository candlestickRepository;
    private final FilterRegistry filterRegistry;

    public CombinedScanService(CandlestickRepository candlestickRepository, FilterRegistry filterRegistry) {
        this.candlestickRepository = candlestickRepository;
        this.filterRegistry = filterRegistry;
    }

    /**
     * Executes a combined scan with multiple filters.
     *
     * @param request The scan request with filters and time range
     * @return List of candlesticks matching all filter criteria
     */
    public List<FilterMatch> executeScan(CombinedScanRequest request) {
        // Fetch full dataset once (single DB query)
        List<Candlestick> fullData = candlestickRepository
                .findByTimestampBetween(request.getStartTimestamp(), request.getEndTimestamp());

        if (fullData.isEmpty()) {
            return new ArrayList<>();
        }

        // Start with full data as initial results
        List<Candlestick> currentResults = new ArrayList<>(fullData);
        List<FilterMatch> matches = new ArrayList<>();

        // Apply each filter sequentially
        for (FilterRequest filterRequest : request.getFilters()) {
            FilterStrategy filter = filterRegistry.getFilter(filterRequest.getType());

            // Apply filter based on whether it needs full time series
            List<FilterMatch> filterMatches;
            if (filter.needsFullTimeSeries()) {
                // Time-series filter: pass current results but allow access to full data
                filterMatches = filter.apply(currentResults, fullData, filterRequest.getParams());
            } else {
                // Candlestick filter: only needs current results
                filterMatches = filter.apply(currentResults, currentResults, filterRequest.getParams());
            }

            // Update current results to be only the matches from this filter
            currentResults = convertMatchesToCandlesticks(filterMatches);
            matches = filterMatches;  // Keep track of enriched matches

            // If no matches, short-circuit
            if (currentResults.isEmpty()) {
                break;
            }
        }

        return matches;
    }

    /**
     * Converts FilterMatch objects back to Candlestick entities for next filter.
     */
    private List<Candlestick> convertMatchesToCandlesticks(List<FilterMatch> matches) {
        List<Candlestick> candlesticks = new ArrayList<>();
        for (FilterMatch match : matches) {
            Candlestick c = new Candlestick();
            c.setSymbol(match.getSymbol());
            c.setTimestamp(match.getTimestamp());
            c.setOpen(match.getOpen());
            c.setHigh(match.getHigh());
            c.setLow(match.getLow());
            c.setClose(match.getClose());
            c.setVolume(match.getVolume());
            candlesticks.add(c);
        }
        return candlesticks;
    }
}
