package com.joakimcolloz.stocker.analysis.filter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Supported combined-scan filter identifiers.
 * <p>
 * Resolved from request strings by {@link FilterRegistry}.
 */
public enum FilterType {
	HIGH_VOLUME("HIGH_VOLUME", "High Volume",
			"Finds candlesticks with volume significantly above average",
			Map.of("percentageAbove", "Integer — minimum percentage above average volume")),

	RSI("RSI", "RSI",
			"Filters by Relative Strength Index value",
			Map.of("threshold", "Double — RSI threshold value",
					"condition", "String — \"above\" or \"below\"")),

	MACD("MACD", "MACD",
			"Filters by MACD indicator values and crossovers",
			Map.of("condition", "String — above_zero, below_zero, crossover_above, crossover_below, histogram_positive, histogram_negative",
					"fastPeriod", "Integer? — fast EMA period (default 12)",
					"slowPeriod", "Integer? — slow EMA period (default 26)",
					"signalPeriod", "Integer? — signal EMA period (default 9)")),

	PATTERN("PATTERN", "Candlestick Pattern",
			"Detects candlestick patterns (use GET /api/analysis/patterns/supported for available patterns)",
			Map.of("pattern", "String — pattern code, e.g. HAMMER, BULLISH_ENGULFING"));

	private final String code;
	private final String name;
	private final String description;
	private final Map<String, String> paramDescriptions;

	FilterType(String code, String name, String description, Map<String, String> paramDescriptions) {
		this.code = code;
		this.name = name;
		this.description = description;
		this.paramDescriptions = paramDescriptions;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public Map<String, String> getParamDescriptions() {
		return paramDescriptions;
	}

	/**
	 * Parses the wire value (request payload) into a {@link FilterType}.
	 * Kept strict to preserve existing API behavior.
	 */
	public static FilterType fromWireValue(String value) {
		if (value == null) {
			throw new IllegalArgumentException("Unknown filter type: null");
		}

		try {
			return FilterType.valueOf(value);
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Unknown filter type: " + value);
		}
	}

	public static List<FilterType> allTypes() {
		return Arrays.asList(values());
	}
}
