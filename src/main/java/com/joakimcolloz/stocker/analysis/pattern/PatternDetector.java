package com.joakimcolloz.stocker.analysis.pattern;

import com.joakimcolloz.stocker.analysis.dto.pattern.PatternMatch;
import com.joakimcolloz.stocker.api.entity.Candlestick;

import java.util.List;

/**
 * Strategy interface for detecting a single {@link PatternType}.
 * Discovered and used via {@link PatternRegistry}.
 */
public interface PatternDetector {
	PatternType getPatternType();

	List<PatternMatch> detect(List<Candlestick> candlesticks);
}
