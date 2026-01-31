package com.joakimcolloz.stocker.analysis.filter;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class FilterRegistry {
	private final Map<FilterType, FilterStrategy> filters;

	public FilterRegistry() {
		EnumMap<FilterType, FilterStrategy> map = new EnumMap<>(FilterType.class);
		map.put(FilterType.HIGH_VOLUME, new HighVolumeFilter());
		map.put(FilterType.RSI, new RSIFilter());
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
