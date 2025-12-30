package com.joakimcolloz.stocker.analysis.dto;

import lombok.Data;

import java.util.List;

/**
 * Request payload for combined filter scanning.
 */
@Data
public class CombinedScanRequest {
    private List<FilterRequest> filters;
    private long startTimestamp;
    private long endTimestamp;
}
