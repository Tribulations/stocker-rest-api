package com.joakimcolloz.stocker.analysis.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Represents a candlestick with volume significantly above average.
 *
 * @author Joakim Colloz
 * @version 1.0
 */
@Data
@AllArgsConstructor
public class HighVolumeCandlestick {
    private String symbol;
    private long timestamp;
    private long volume;
    private long averageVolume;
    private int percentageAboveAverage;
}
