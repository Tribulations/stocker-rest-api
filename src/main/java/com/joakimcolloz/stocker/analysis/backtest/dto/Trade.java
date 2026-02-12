package com.joakimcolloz.stocker.analysis.backtest.dto;

public record Trade(
        String symbol,
        long entryTimestamp,
        double entryPrice,
        long exitTimestamp,
        double exitPrice,
        String exitReason,
        double returnPercent,
        int holdingDays
) {
}
