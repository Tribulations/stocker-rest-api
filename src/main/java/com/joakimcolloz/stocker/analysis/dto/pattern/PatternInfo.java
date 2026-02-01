package com.joakimcolloz.stocker.analysis.dto.pattern;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Information about a supported pattern type.
 *
 * @author Joakim Colloz
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatternInfo {
    private String code;
    private String name;
    private String description;
    private String type;
}
