package com.joakimcolloz.stocker.analysis.filter;

/**
 * Supported combined-scan filter identifiers.
 * <p>
 * Resolved from request strings by {@link FilterRegistry}.
 */
public enum FilterType {
	HIGH_VOLUME,
	RSI,
	MACD;

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
}
