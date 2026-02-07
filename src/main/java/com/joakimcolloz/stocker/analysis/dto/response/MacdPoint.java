package com.joakimcolloz.stocker.analysis.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MacdPoint {
	private long timestamp;
	private double macd;
	private double signal;
	private double histogram;
}
