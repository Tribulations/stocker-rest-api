package com.joakimcolloz.stocker.analysis.dto.response;

import com.joakimcolloz.stocker.analysis.dto.pattern.PatternMatch;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response containing pattern scan results.
 *
 * @author Joakim Colloz
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScanResponse {
    private List<PatternMatch> matches;
    private int totalMatches;
    private String message;

    public ScanResponse(List<PatternMatch> matches) {
        this.matches = matches;
        this.totalMatches = matches.size();
        this.message = totalMatches > 0 ? "Patterns found" : "No patterns detected";
    }
}
