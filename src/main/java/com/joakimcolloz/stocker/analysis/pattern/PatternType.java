package com.joakimcolloz.stocker.analysis.pattern;

import com.joakimcolloz.stocker.analysis.dto.pattern.PatternInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Supported candlestick patterns and their public metadata.
 * <p>
 * Used by {@link com.joakimcolloz.stocker.analysis.controller.AnalysisController} to list supported patterns
 * and by {@link PatternRegistry} to resolve detectors.
 */
public enum PatternType {
	HAMMER("HAMMER", "Hammer", "Bullish reversal with long lower shadow", "BULLISH"),
	BULLISH_ENGULFING("BULLISH_ENGULFING", "Bullish Engulfing", "Bullish reversal with current candle engulfing previous", "BULLISH");

	private final String code;
	private final String name;
	private final String description;
	private final String type;

	PatternType(String code, String name, String description, String type) {
		this.code = code;
		this.name = name;
		this.description = description;
		this.type = type;
	}

	public String getCode() {
		return code;
	}

	public PatternInfo toPatternInfo() {
		return new PatternInfo(code, name, description, type);
	}

	public static Optional<PatternType> fromWireValue(String value) {
		if (value == null) {
			return Optional.empty();
		}
		return Arrays.stream(values())
				.filter(p -> p.code.equalsIgnoreCase(value))
				.findFirst();
	}

	public static List<PatternInfo> supportedPatterns() {
		return Arrays.stream(values())
				.map(PatternType::toPatternInfo)
				.toList();
	}
}
