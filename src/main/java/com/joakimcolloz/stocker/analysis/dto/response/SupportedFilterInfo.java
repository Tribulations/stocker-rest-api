package com.joakimcolloz.stocker.analysis.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Describes a supported combined-scan filter type, its purpose, and expected parameters.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupportedFilterInfo {
	private String type;
	private String name;
	private String description;
	private Map<String, String> params;
}
