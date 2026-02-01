package com.joakimcolloz.stocker.analysis.dto.filter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HighVolumeFilterParams implements FilterParams {
	private int percentageAbove;
}
