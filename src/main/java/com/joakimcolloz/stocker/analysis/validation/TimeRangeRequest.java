package com.joakimcolloz.stocker.analysis.validation;

/**
 * Marker interface for request DTOs that represent a time range.
 * Used by {@link ValidTimeRange} to apply a shared start/end validation rule.
 */
public interface TimeRangeRequest {
	long getStartTimestamp();

	long getEndTimestamp();
}
