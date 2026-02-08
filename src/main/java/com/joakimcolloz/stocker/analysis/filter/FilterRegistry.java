package com.joakimcolloz.stocker.analysis.filter;

import com.joakimcolloz.stocker.analysis.pattern.PatternRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registry that maps a requested filter type to its {@link FilterStrategy} implementation.
 * Used by {@link com.joakimcolloz.stocker.analysis.service.CombinedScanService}.
 */
@Component
public class FilterRegistry {
	private final Map<FilterType, FilterStrategy> filters;

	public FilterRegistry(PatternRegistry patternRegistry) {
		EnumMap<FilterType, FilterStrategy> map = new EnumMap<>(FilterType.class);
		map.put(FilterType.HIGH_VOLUME, new HighVolumeFilter());
		map.put(FilterType.RSI, new RSIFilter());
		map.put(FilterType.MACD, new MacdFilter());
		map.put(FilterType.PATTERN, new PatternFilter(patternRegistry));
		this.filters = Map.copyOf(map);
	}

	public FilterStrategy getFilter(String type) {
		FilterType filterType = FilterType.fromWireValue(type);
		FilterStrategy filter = filters.get(filterType);
		if (filter == null) {
			throw new IllegalArgumentException("Unknown filter type: " + type);
		}
		return filter;
	}
}
