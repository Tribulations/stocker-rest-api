package com.joakimcolloz.stocker.analysis.error;

public enum AnalysisErrorCode {
    RSI_TOO_FEW_DATA_POINTS("Too few data points to calculate RSI");

    private final String defaultMessage;

    AnalysisErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
