package com.joakimcolloz.stocker.analysis.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics about average trading volume for a stock symbol.
 *
 * @author Joakim Colloz
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AverageVolumeStatistic {
    private String symbol;
    private long averageVolume;
    private int dataPoints;
}
