package com.joakimcolloz.stocker.analysis.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RsiSeriesResponse {
	private String symbol;
	private int period;
	private long startTimestamp;
	private long endTimestamp;
	private List<RsiPoint> values;
}
