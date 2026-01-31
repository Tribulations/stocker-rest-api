package com.joakimcolloz.stocker.analysis.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for {@link ValidTimeRange}.
 * <p>
 * The rule is: {@code startTimestamp <= endTimestamp}.
 */
public class ValidTimeRangeValidator implements ConstraintValidator<ValidTimeRange, TimeRangeRequest> {
	@Override
	public boolean isValid(TimeRangeRequest value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}

		return value.getStartTimestamp() <= value.getEndTimestamp();
	}
}
