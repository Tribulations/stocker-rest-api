package com.joakimcolloz.stocker.analysis.backtest.dto;

/**
 * Exit criteria for a backtest position. At least one field must be non-null.
 *
 * @param stopLossPercent  sell if price drops this percentage from entry (e.g. 5.0 = 5%)
 * @param takeProfitPercent sell if price rises this percentage from entry (e.g. 15.0 = 15%)
 * @param maxHoldingDays   force exit after N calendar days
 */
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
