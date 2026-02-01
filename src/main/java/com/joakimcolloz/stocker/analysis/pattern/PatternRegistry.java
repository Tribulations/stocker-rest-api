package com.joakimcolloz.stocker.analysis.pattern;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry that resolves request pattern codes into {@link PatternDetector} strategies.
 * Used by {@link com.joakimcolloz.stocker.analysis.service.AnalysisService}.
 */
@Component
public class PatternRegistry {
	private final Map<PatternType, PatternDetector> detectors;

	public PatternRegistry(List<PatternDetector> detectors) {
		EnumMap<PatternType, PatternDetector> map = new EnumMap<>(PatternType.class);
		for (PatternDetector detector : detectors) {
			map.put(detector.getPatternType(), detector);
		}
		this.detectors = Map.copyOf(map);
	}

	public Optional<PatternDetector> findDetector(String wireValue) {
		return PatternType.fromWireValue(wireValue).map(detectors::get);
	}
}
