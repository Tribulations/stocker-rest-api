package com.joakimcolloz.stocker.analysis.backtest.dto;

/**
 * Controls how and when a position is entered after a signal fires.
 *
 * @param entryMode           "NEXT_BAR_OPEN" or "LIMIT_ORDER"
 * @param limitOffsetPercent  limit = signalClose * (1 ± this/100). Default 0.0. Ignored for NEXT_BAR_OPEN.
 * @param penetrationPercent  fill requires price to exceed limit by this %. Default 0.0. Ignored for NEXT_BAR_OPEN.
 * @param maxBarsToFill       bars after signal to wait for fill. Default 1. Ignored for NEXT_BAR_OPEN.
 * @param limitDirection      "ABOVE" or "BELOW" (null defaults to "BELOW"). Ignored for NEXT_BAR_OPEN.
 */
public record EntryRule(
        String entryMode,
        Double limitOffsetPercent,
        Double penetrationPercent,
        Integer maxBarsToFill,
        String limitDirection
) {
    public EntryRule {
        if (entryMode != null && !entryMode.equals("NEXT_BAR_OPEN") && !entryMode.equals("LIMIT_ORDER")) {
            throw new IllegalArgumentException(
                    "entryMode must be NEXT_BAR_OPEN or LIMIT_ORDER, got: " + entryMode);
        }
        if (limitDirection != null && !limitDirection.equals("ABOVE") && !limitDirection.equals("BELOW")) {
            throw new IllegalArgumentException(
                    "limitDirection must be ABOVE or BELOW, got: " + limitDirection);
        }
    }
}
