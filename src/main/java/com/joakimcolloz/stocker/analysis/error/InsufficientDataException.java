package com.joakimcolloz.stocker.analysis.error;

import java.util.Map;

public class InsufficientDataException extends AnalysisException {

    public InsufficientDataException(AnalysisErrorCode code) {
        super(code);
    }

    public InsufficientDataException(AnalysisErrorCode code, Map<String, Object> details) {
        super(code, details);
    }

    public InsufficientDataException(AnalysisErrorCode code, String message) {
        super(code, message);
    }

    public InsufficientDataException(AnalysisErrorCode code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
