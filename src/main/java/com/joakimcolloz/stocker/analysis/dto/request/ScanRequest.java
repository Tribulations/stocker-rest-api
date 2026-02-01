package com.joakimcolloz.stocker.analysis.dto.request;

import com.joakimcolloz.stocker.analysis.validation.TimeRangeRequest;
import com.joakimcolloz.stocker.analysis.validation.ValidTimeRange;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Request payload for pattern scanning.
 *
 * @author Joakim Colloz
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ValidTimeRange
public class ScanRequest implements TimeRangeRequest {
	@NotEmpty
	private Set<String> patterns;

	@Positive
	private long startTimestamp;

	@Positive
	private long endTimestamp;
}
