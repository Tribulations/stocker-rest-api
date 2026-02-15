package com.joakimcolloz.stocker.analysis.backtest.dto;

public record ExecutionConfig(
        ExecutionMode mode,
        String intradayInterval,
        Double slippageBps,
        Double spreadBps,
        Double commissionBps,
        Boolean conservativeFillMode
) {
    public ExecutionConfig {
        if (mode == null) {
            mode = ExecutionMode.DAILY_ONLY;
        }
        if (intradayInterval == null) {
            intradayInterval = "5m";
        }
        if (slippageBps == null) {
            slippageBps = 0.0;
        }
        if (spreadBps == null) {
            spreadBps = 0.0;
        }
        if (commissionBps == null) {
            commissionBps = 0.0;
        }
        if (conservativeFillMode == null) {
            conservativeFillMode = true;
        }

        if (!"5m".equals(intradayInterval)) {
            throw new IllegalArgumentException("intradayInterval must be '5m'");
        }
        if (slippageBps < 0.0) {
            throw new IllegalArgumentException("slippageBps must be >= 0");
        }
        if (spreadBps < 0.0) {
            throw new IllegalArgumentException("spreadBps must be >= 0");
        }
        if (commissionBps < 0.0) {
            throw new IllegalArgumentException("commissionBps must be >= 0");
        }
    }
}
