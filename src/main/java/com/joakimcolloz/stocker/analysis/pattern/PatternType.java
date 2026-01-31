package com.joakimcolloz.stocker.analysis.pattern;

import com.joakimcolloz.stocker.analysis.dto.PatternInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum PatternType {
	HAMMER("HAMMER", "Hammer", "Bullish reversal with long lower shadow", "BULLISH");

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
