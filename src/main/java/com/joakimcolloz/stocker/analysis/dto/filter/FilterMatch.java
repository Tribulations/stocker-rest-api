package com.joakimcolloz.stocker.analysis.dto.filter;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * Represents a candlestick that matches filter criteria.
 */
@Data
@AllArgsConstructor
public class FilterMatch {
    private String symbol;
    private long timestamp;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
    private String filterType;  // Which filter matched this
    private String description;  // Human-readable description
	private Map<String, Double> values;
	private Map<String, String> params;
}
