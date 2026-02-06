package com.joakimcolloz.stocker.analysis.service;

import com.joakimcolloz.stocker.api.dao.CandlestickRepository;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterMatch;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterRequest;
import com.joakimcolloz.stocker.analysis.dto.request.CombinedScanRequest;
import com.joakimcolloz.stocker.analysis.dto.response.CombinedFilterResult;
import com.joakimcolloz.stocker.analysis.dto.response.FilterEvidence;
import com.joakimcolloz.stocker.analysis.filter.FilterRegistry;
import com.joakimcolloz.stocker.analysis.filter.FilterStrategy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public List<CombinedFilterResult> executeScan(CombinedScanRequest request) {
        // Fetch full dataset once (single DB query)
        List<Candlestick> fullData = candlestickRepository
                .findByTimestampBetween(request.getStartTimestamp(), request.getEndTimestamp());

        if (fullData.isEmpty()) {
            return new ArrayList<>();
        }

        // Start with full data as initial results
        List<Candlestick> currentResults = new ArrayList<>(fullData);
        Map<CandlestickKey, Candlestick> fullDataLookup = indexByKey(fullData);
        Map<CandlestickKey, CombinedFilterResult> accumulatedResults = new HashMap<>();

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

            Map<CandlestickKey, CombinedFilterResult> nextAccumulatedResults = new HashMap<>();
            for (FilterMatch match : filterMatches) {
                CandlestickKey key = new CandlestickKey(match.getSymbol(), match.getTimestamp());
                CombinedFilterResult result = accumulatedResults.get(key);
                if (result == null) {
                    Candlestick candle = fullDataLookup.get(key);
                    if (candle == null) {
                        continue;
                    }
                    result = new CombinedFilterResult(
                            candle.getSymbol(),
                            candle.getTimestamp(),
                            candle.getOpen(),
                            candle.getHigh(),
                            candle.getLow(),
                            candle.getClose(),
                            candle.getVolume(),
                            new ArrayList<>()
                    );
                }

                result.getFilters().add(new FilterEvidence(match.getFilterType(), match.getDescription(), match.getValues(), match.getParams()));
                nextAccumulatedResults.put(key, result);
            }

            // Update current results to be only the matches from this filter
            currentResults = resolveMatchedCandlesticks(filterMatches, currentResults, fullData);
            accumulatedResults = nextAccumulatedResults;

            // If no matches, short-circuit
            if (currentResults.isEmpty()) {
                break;
            }
        }

        List<CombinedFilterResult> orderedResults = new ArrayList<>();
        for (Candlestick candle : currentResults) {
            CombinedFilterResult result = accumulatedResults.get(new CandlestickKey(candle.getSymbol(), candle.getTimestamp()));
            if (result != null) {
                orderedResults.add(result);
            }
        }

        return orderedResults;
    }

    /**
     * Resolves the matched candlesticks back to the already-loaded entities.
     * <p>
     * This avoids creating new Candlestick instances between filters.
     */
    private List<Candlestick> resolveMatchedCandlesticks(
            List<FilterMatch> matches,
            List<Candlestick> currentResults,
            List<Candlestick> fullData
    ) {
        Map<CandlestickKey, Candlestick> currentLookup = indexByKey(currentResults);
        Map<CandlestickKey, Candlestick> fullLookup = indexByKey(fullData);

        List<Candlestick> resolved = new ArrayList<>();
        for (FilterMatch match : matches) {
            CandlestickKey key = new CandlestickKey(match.getSymbol(), match.getTimestamp());
            Candlestick candle = currentLookup.get(key);
            if (candle == null) {
                candle = fullLookup.get(key);
            }
            if (candle != null) {
                resolved.add(candle);
            }
        }
        return resolved;
    }

    private Map<CandlestickKey, Candlestick> indexByKey(List<Candlestick> candlesticks) {
        Map<CandlestickKey, Candlestick> index = new HashMap<>();
        for (Candlestick candle : candlesticks) {
            index.put(new CandlestickKey(candle.getSymbol(), candle.getTimestamp()), candle);
        }
        return index;
    }

    private record CandlestickKey(String symbol, long timestamp) {
    }
}
