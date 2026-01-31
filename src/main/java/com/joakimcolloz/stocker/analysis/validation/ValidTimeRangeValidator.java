package com.joakimcolloz.stocker.analysis.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidTimeRangeValidator implements ConstraintValidator<ValidTimeRange, TimeRangeRequest> {
	@Override
	public boolean isValid(TimeRangeRequest value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}

		return value.getStartTimestamp() <= value.getEndTimestamp();
	}
}
