package com.joakimcolloz.stocker.analysis.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CombinedFilterResult {
	private String symbol;
	private long timestamp;
	private double open;
	private double high;
	private double low;
	private double close;
	private long volume;
	private List<FilterEvidence> filters;
}
