package com.joakimcolloz.stocker.analysis.dto.filter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovingAverageFilterParams implements FilterParams {
	private Integer period;    // e.g. 20, 50, 200
	private String maType;     // "SMA" or "EMA"
	private String condition;  // "price_above_ma", "price_below_ma",
	                           // "price_crosses_above_ma", "price_crosses_below_ma"
}
