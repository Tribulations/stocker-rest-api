package com.joakimcolloz.stocker.analysis.backtest.dto;

/**
 * Exit criteria for a backtest position. At least one field must be non-null.
 *
 * @param stopLossPercent   sell if price drops this percentage from entry (e.g. 5.0 = 5%)
 * @param takeProfitPercent sell if price rises this percentage from entry (e.g. 15.0 = 15%)
 * @param maxHoldingDays    force exit after N calendar days
 * @param trailingStopLoss  if true, stop-loss ratchets up with price highs; null/false = fixed stop
 */
public record ExitRule(
        Double stopLossPercent,
        Double takeProfitPercent,
        Integer maxHoldingDays,
        Boolean trailingStopLoss
) {
    public ExitRule {
        if (stopLossPercent == null && takeProfitPercent == null && maxHoldingDays == null) {
            throw new IllegalArgumentException(
                    "At least one exit rule must be specified: stopLossPercent, takeProfitPercent, or maxHoldingDays");
        }
        if (Boolean.TRUE.equals(trailingStopLoss) && stopLossPercent == null) {
            throw new IllegalArgumentException(
                    "trailingStopLoss requires stopLossPercent to be set");
        }
    }
}
