package com.joakimcolloz.stocker.analysis.pattern;

import com.joakimcolloz.stocker.analysis.dto.PatternMatch;
import com.joakimcolloz.stocker.analysis.service.PatternDetectionService;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HammerPatternDetector implements PatternDetector {
	private final PatternDetectionService patternDetectionService;

	public HammerPatternDetector(PatternDetectionService patternDetectionService) {
		this.patternDetectionService = patternDetectionService;
	}

	@Override
	public PatternType getPatternType() {
		return PatternType.HAMMER;
	}

	@Override
	public List<PatternMatch> detect(List<Candlestick> candlesticks) {
		return patternDetectionService.detectHammer(candlesticks);
	}
}
