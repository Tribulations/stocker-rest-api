package com.joakimcolloz.stocker.analysis.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MacdSeriesResponse {
	private String symbol;
	private int fastPeriod;
	private int slowPeriod;
	private int signalPeriod;
	private long startTimestamp;
	private long endTimestamp;
	private List<MacdPoint> values;
}
