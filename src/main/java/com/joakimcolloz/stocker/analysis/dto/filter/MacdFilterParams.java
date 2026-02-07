package com.joakimcolloz.stocker.analysis.dto.filter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MacdFilterParams implements FilterParams {
	private String condition;
	private Integer fastPeriod;
	private Integer slowPeriod;
	private Integer signalPeriod;
}
