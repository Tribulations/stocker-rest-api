package com.joakimcolloz.stocker.analysis.dto.pattern;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a detected candlestick pattern match.
 *
 * @author Joakim Colloz
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatternMatch {
    private String symbol;
    private String patternType;
    private long timestamp;
    private int confidence;
    private String description;
}
