package com.joakimcolloz.stocker.analysis.service;

import com.joakimcolloz.stocker.analysis.dto.PatternMatch;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for detecting candlestick patterns.
 * <p>
 * Currently supports:
 * <ul>
 *   <li>Hammer pattern</li>
 * </ul>
 *
 * @author Joakim Colloz
 * @version 1.0
 */
@Service
public class PatternDetectionService {

    /**
     * Detects Hammer candlestick patterns in the provided data.
     * <p>
     * Hammer criteria:
     * <ul>
     *   <li>Long lower shadow (at least 2x body size)</li>
     *   <li>Small upper shadow (less than 30% of body size)</li>
     *   <li>Small body</li>
     * </ul>
     *
     * @param candlesticks list of candlesticks to analyze
     * @return list of detected hammer patterns
     */
    public List<PatternMatch> detectHammer(List<Candlestick> candlesticks) {
        List<PatternMatch> matches = new ArrayList<>();

        for (Candlestick candle : candlesticks) {
            double bodySize = Math.abs(candle.getClose() - candle.getOpen());
            double totalRange = candle.getHigh() - candle.getLow();

            if (totalRange == 0) continue;

            double lowerShadow = Math.min(candle.getOpen(), candle.getClose()) - candle.getLow();
            double upperShadow = candle.getHigh() - Math.max(candle.getOpen(), candle.getClose());

            // Hammer criteria
            if (lowerShadow > bodySize * 2 &&
                    upperShadow < bodySize * 0.3 &&
                    bodySize > 0) {

                int confidence = calculateHammerConfidence(bodySize, lowerShadow, upperShadow, totalRange);

                matches.add(new PatternMatch(
                        candle.getSymbol(),
                        "HAMMER",
                        candle.getTimestamp(),
                        confidence,
                        "Bullish reversal pattern"
                ));
            }
        }

        return matches;
    }

    /**
     * Calculates confidence score for a hammer pattern.
     *
     * @param bodySize    size of the candlestick body
     * @param lowerShadow length of the lower shadow
     * @param upperShadow length of the upper shadow
     * @param totalRange  total high-low range
     * @return confidence score (0-99)
     */
    private int calculateHammerConfidence(double bodySize, double lowerShadow,
                                          double upperShadow, double totalRange) {
        double lowerRatio = lowerShadow / totalRange;
        double bodyRatio = bodySize / totalRange;
        double upperRatio = upperShadow / totalRange;

        int confidence = 50; // Base confidence

        if (lowerRatio > 0.6) confidence += 20;
        if (bodyRatio < 0.2) confidence += 15;
        if (upperRatio < 0.05) confidence += 15;

        return Math.min(confidence, 99);
    }
}
