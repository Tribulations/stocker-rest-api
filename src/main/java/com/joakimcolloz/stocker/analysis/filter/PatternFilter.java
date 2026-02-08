package com.joakimcolloz.stocker.analysis.filter;

import com.joakimcolloz.stocker.analysis.dto.filter.FilterMatch;
import com.joakimcolloz.stocker.analysis.dto.filter.FilterParams;
import com.joakimcolloz.stocker.analysis.dto.filter.PatternFilterParams;
import com.joakimcolloz.stocker.analysis.dto.pattern.PatternMatch;
import com.joakimcolloz.stocker.analysis.pattern.PatternDetector;
import com.joakimcolloz.stocker.analysis.pattern.PatternRegistry;
import com.joakimcolloz.stocker.api.entity.Candlestick;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bridges pattern detection into the combined-scan filter system.
 * <p>
 * Looks up a {@link PatternDetector} via {@link PatternRegistry}, runs detection
 * on the full data grouped by symbol, then intersects results with filteredData.
 */
public class PatternFilter extends FilterStrategy {

	private final PatternRegistry patternRegistry;

	public PatternFilter(PatternRegistry patternRegistry) {
		this.patternRegistry = patternRegistry;
	}

	@Override
	public boolean needsFullTimeSeries() {
		return true;
	}

	@Override
	public List<FilterMatch> apply(
			List<Candlestick> filteredData,
			List<Candlestick> fullData,
			FilterParams params
	) {
		if (!(params instanceof PatternFilterParams patternParams)) {
			throw new IllegalArgumentException("Invalid parameters for PATTERN filter");
		}

		String patternCode = patternParams.getPattern();
		if (patternCode == null || patternCode.isBlank()) {
			throw new IllegalArgumentException("Missing pattern code for PATTERN filter");
		}

		PatternDetector detector = patternRegistry.findDetector(patternCode)
				.orElseThrow(() -> new IllegalArgumentException("Unknown pattern: " + patternCode));

		// Build set of (symbol, timestamp) pairs from filteredData for intersection
		Set<String> filteredKeys = filteredData.stream()
				.map(c -> c.getSymbol() + ":" + c.getTimestamp())
				.collect(Collectors.toSet());

		// Group fullData by symbol and run detector on each group
		Map<String, List<Candlestick>> bySymbol = fullData.stream()
				.collect(Collectors.groupingBy(Candlestick::getSymbol));

		// Build a lookup from fullData for candlestick details
		Map<String, Candlestick> candleLookup = fullData.stream()
				.collect(Collectors.toMap(
						c -> c.getSymbol() + ":" + c.getTimestamp(),
						c -> c,
						(a, b) -> a
				));

		List<FilterMatch> matches = new ArrayList<>();

		for (List<Candlestick> symbolCandles : bySymbol.values()) {
			List<PatternMatch> detected = detector.detect(symbolCandles);

			for (PatternMatch pm : detected) {
				String key = pm.getSymbol() + ":" + pm.getTimestamp();
				if (!filteredKeys.contains(key)) {
					continue;
				}

				Candlestick candle = candleLookup.get(key);
				if (candle == null) {
					continue;
				}

				matches.add(new FilterMatch(
						candle.getSymbol(),
						candle.getTimestamp(),
						candle.getOpen(),
						candle.getHigh(),
						candle.getLow(),
						candle.getClose(),
						candle.getVolume(),
						"PATTERN",
						pm.getDescription() + " (" + patternCode + ")",
						Map.of("confidence", (double) pm.getConfidence()),
						Map.of("pattern", patternCode)
				));
			}
		}

		return matches;
	}
}
