package com.joakimcolloz.stocker.analysis.dto;

import com.joakimcolloz.stocker.analysis.validation.TimeRangeRequest;
import com.joakimcolloz.stocker.analysis.validation.ValidTimeRange;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/**
 * Request payload for high volume candlestick detection.
 *
 * @author Joakim Colloz
 * @version 1.0
 */
@Data
@ValidTimeRange
public class HighVolumeRequest implements TimeRangeRequest {
	@Positive
	private long startTimestamp;

	@Positive
	private long endTimestamp;

	@PositiveOrZero
	private int percentageAbove; // e.g., 50 means 50% above average
}
