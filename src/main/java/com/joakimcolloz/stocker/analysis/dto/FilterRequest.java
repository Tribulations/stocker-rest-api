// File: src/main/java/com/joakimcolloz/stocker/analysis/dto/FilterRequest.java

package com.joakimcolloz.stocker.analysis.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single filter in a combined scan request.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilterRequest {
    private String type;  // e.g., "HIGH_VOLUME", "RSI"
    private JsonNode params;  // Filter-specific parameters as flexible JSON
}
