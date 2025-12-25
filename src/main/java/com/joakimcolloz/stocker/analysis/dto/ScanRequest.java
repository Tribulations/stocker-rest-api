package com.joakimcolloz.stocker.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Request payload for pattern scanning.
 *
 * @author Joakim Colloz
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScanRequest {
    private Set<String> patterns;
    private long startTimestamp;
    private long endTimestamp;
}
