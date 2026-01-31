package com.joakimcolloz.stocker.analysis.validation;

public interface TimeRangeRequest {
	long getStartTimestamp();

	long getEndTimestamp();
}
