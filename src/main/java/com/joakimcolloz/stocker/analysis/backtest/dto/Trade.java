package com.joakimcolloz.stocker.analysis.backtest.dto;

/**
 * A completed backtest trade.
 *
 * @param symbol         ticker symbol
 * @param entryTimestamp  epoch seconds of entry bar
 * @param entryPrice     entry price (signal bar's close)
 * @param exitTimestamp   epoch seconds of exit bar
 * @param exitPrice      exit price (stop/target level or bar close)
 * @param exitReason     one of STOP_LOSS, TRAILING_STOP_LOSS, TAKE_PROFIT, MAX_HOLDING_DAYS, END_OF_DATA
 * @param returnPercent  percentage return: (exit - entry) / entry * 100
 * @param holdingDays    number of calendar days held
 */
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
