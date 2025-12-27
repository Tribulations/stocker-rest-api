package com.joakimcolloz.stocker.analysis.dto;

import lombok.Data;

/**
 * Request payload for high volume candlestick detection.
 *
 * @author Joakim Colloz
 * @version 1.0
 */
@Data
public class HighVolumeRequest {
    private long startTimestamp;
    private long endTimestamp;
    private int percentageAbove; // e.g., 50 means 50% above average
}
