package com.joakimcolloz.stocker.analysis.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a request DTO's time range is ordered correctly.
 * Intended to be used together with field-level constraints (e.g. {@code @Positive})
 * to keep request validation centralized and consistent.
 */
@Documented
@Constraint(validatedBy = {ValidTimeRangeValidator.class})
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTimeRange {
	String message() default "Invalid time range";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
