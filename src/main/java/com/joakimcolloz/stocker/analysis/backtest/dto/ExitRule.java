package com.joakimcolloz.stocker.analysis.backtest.dto;

public record ExitRule(
        Double stopLossPercent,
        Double takeProfitPercent,
        Integer maxHoldingDays
) {
    public ExitRule {
        if (stopLossPercent == null && takeProfitPercent == null && maxHoldingDays == null) {
            throw new IllegalArgumentException(
                    "At least one exit rule must be specified: stopLossPercent, takeProfitPercent, or maxHoldingDays");
        }
    }
}
