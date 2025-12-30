package com.joakimcolloz.stocker.analysis.filter;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating filter strategy instances based on filter type.
 */
public class FilterFactory {

    private static final Map<String, FilterStrategy> filters = new HashMap<>();

    static {
        filters.put("HIGH_VOLUME", new HighVolumeFilter());
        filters.put("RSI", new RSIFilter());
        // We add more filters here as they're implemented
    }

    /**
     * Gets a filter strategy instance by type.
     *
     * @param type The filter type identifier
     * @return FilterStrategy instance
     * @throws IllegalArgumentException if filter type is unknown
     */
    public static FilterStrategy getFilter(String type) {
        FilterStrategy filter = filters.get(type);
        if (filter == null) {
            throw new IllegalArgumentException("Unknown filter type: " + type);
        }
        return filter;
    }
}
