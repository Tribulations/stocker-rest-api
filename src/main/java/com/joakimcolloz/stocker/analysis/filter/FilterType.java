package com.joakimcolloz.stocker.analysis.filter;

public enum FilterType {
	HIGH_VOLUME,
	RSI;

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
