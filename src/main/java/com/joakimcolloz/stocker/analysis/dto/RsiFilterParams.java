package com.joakimcolloz.stocker.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RsiFilterParams implements FilterParams {
	private double threshold;
	private String condition;
}
