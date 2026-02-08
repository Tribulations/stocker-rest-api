package com.joakimcolloz.stocker.analysis.pattern;

import com.joakimcolloz.stocker.analysis.dto.pattern.PatternMatch;
import com.joakimcolloz.stocker.analysis.service.PatternDetectionService;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link PatternDetector} implementation for {@link PatternType#BULLISH_ENGULFING}.
 * Delegates detection to {@link PatternDetectionService}.
 */
@Component
public class BullishEngulfingDetector implements PatternDetector {
	private final PatternDetectionService patternDetectionService;

	public BullishEngulfingDetector(PatternDetectionService patternDetectionService) {
		this.patternDetectionService = patternDetectionService;
	}

	@Override
	public PatternType getPatternType() {
		return PatternType.BULLISH_ENGULFING;
	}

	@Override
	public List<PatternMatch> detect(List<Candlestick> candlesticks) {
		return patternDetectionService.detectBullishEngulfing(candlesticks);
	}
}
