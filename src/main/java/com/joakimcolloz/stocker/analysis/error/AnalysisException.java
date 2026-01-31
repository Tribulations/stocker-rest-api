package com.joakimcolloz.stocker.analysis.error;

import java.util.Map;

public class AnalysisException extends RuntimeException {

    private final AnalysisErrorCode code;

    private final Map<String, Object> details;

    public AnalysisException(AnalysisErrorCode code) {
        super(code.getDefaultMessage());
        this.code = code;
        this.details = Map.of();
    }

    public AnalysisException(AnalysisErrorCode code, Map<String, Object> details) {
        super(code.getDefaultMessage());
        this.code = code;
        this.details = details == null ? Map.of() : details;
    }

    public AnalysisException(AnalysisErrorCode code, String message) {
        super(message);
        this.code = code;
        this.details = Map.of();
    }

    public AnalysisException(AnalysisErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : details;
    }

    public AnalysisErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
