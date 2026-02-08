package com.joakimcolloz.stocker.analysis.service;

import com.joakimcolloz.stocker.analysis.dto.pattern.PatternMatch;
import com.joakimcolloz.stocker.api.entity.Candlestick;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
     * Detects Bullish Engulfing candlestick patterns in the provided data.
     * <p>
     * Bullish Engulfing criteria:
     * <ul>
     *   <li>Previous candle is bearish (close &lt; open)</li>
     *   <li>Current candle is bullish (close &gt; open)</li>
     *   <li>Current body engulfs previous body (open &lt;= prev close AND close &gt;= prev open)</li>
     * </ul>
     *
     * @param candlesticks list of candlesticks to analyze
     * @return list of detected bullish engulfing patterns
     */
    public List<PatternMatch> detectBullishEngulfing(List<Candlestick> candlesticks) {
        List<PatternMatch> matches = new ArrayList<>();

        Map<String, List<Candlestick>> bySymbol = candlesticks.stream()
                .collect(Collectors.groupingBy(Candlestick::getSymbol));

        for (Map.Entry<String, List<Candlestick>> entry : bySymbol.entrySet()) {
            List<Candlestick> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparingLong(Candlestick::getTimestamp))
                    .toList();

            for (int i = 1; i < sorted.size(); i++) {
                Candlestick prev = sorted.get(i - 1);
                Candlestick curr = sorted.get(i);

                boolean prevBearish = prev.getClose() < prev.getOpen();
                boolean currBullish = curr.getClose() > curr.getOpen();
                boolean engulfing = curr.getOpen() <= prev.getClose() && curr.getClose() >= prev.getOpen();

                if (prevBearish && currBullish && engulfing) {
                    int confidence = calculateEngulfingConfidence(prev, curr);
                    matches.add(new PatternMatch(
                            curr.getSymbol(),
                            "BULLISH_ENGULFING",
                            curr.getTimestamp(),
                            confidence,
                            "Bullish engulfing pattern"
                    ));
                }
            }
        }

        return matches;
    }

    private int calculateEngulfingConfidence(Candlestick prev, Candlestick curr) {
        double prevBody = Math.abs(prev.getClose() - prev.getOpen());
        double currBody = Math.abs(curr.getClose() - curr.getOpen());

        int confidence = 50;

        // +20 if current body >= 1.5x previous body
        if (prevBody > 0 && currBody >= 1.5 * prevBody) {
            confidence += 20;
        }

        // +15 if current shadows are small (< 10% of body)
        if (currBody > 0) {
            double currUpperShadow = curr.getHigh() - curr.getClose();
            double currLowerShadow = curr.getOpen() - curr.getLow();
            if (currUpperShadow < 0.1 * currBody && currLowerShadow < 0.1 * currBody) {
                confidence += 15;
            }
        }

        // +15 if current volume > previous volume
        if (curr.getVolume() > prev.getVolume()) {
            confidence += 15;
        }

        return Math.min(confidence, 99);
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
