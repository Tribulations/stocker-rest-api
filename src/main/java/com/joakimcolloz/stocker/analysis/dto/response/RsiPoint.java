package com.joakimcolloz.stocker.analysis.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RsiPoint {
	private long timestamp;
	private double rsi;
}
