// File: src/main/java/com/joakimcolloz/stocker/analysis/dto/FilterRequest.java

package com.joakimcolloz.stocker.analysis.dto.filter;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single filter in a combined scan request.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonDeserialize(using = FilterRequestDeserializer.class)
public class FilterRequest {
    private String type;  // e.g., "HIGH_VOLUME", "RSI"
    private FilterParams params;
}
