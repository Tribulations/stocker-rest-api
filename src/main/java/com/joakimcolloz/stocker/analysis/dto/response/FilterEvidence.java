package com.joakimcolloz.stocker.analysis.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilterEvidence {
	private String filterType;
	private String description;
	private Map<String, Double> values;
	private Map<String, String> params;
}
