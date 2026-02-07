package com.joakimcolloz.stocker.analysis.dto.request;

import com.joakimcolloz.stocker.analysis.dto.filter.FilterRequest;
import com.joakimcolloz.stocker.analysis.validation.TimeRangeRequest;
import com.joakimcolloz.stocker.analysis.validation.ValidTimeRange;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

/**
 * Request payload for combined filter scanning.
 */
@Data
@ValidTimeRange
public class CombinedScanRequest implements TimeRangeRequest {
	@NotEmpty
	private List<FilterRequest> filters;

	@Positive
	private long startTimestamp;

	@Positive
	private long endTimestamp;

	@Min(1)
	private Integer resultLastDays;
}
