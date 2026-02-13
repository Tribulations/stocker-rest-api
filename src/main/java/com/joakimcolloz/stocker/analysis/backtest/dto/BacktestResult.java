package com.joakimcolloz.stocker.analysis.backtest.dto;

import java.util.List;

/**
 * Backtest response containing the trade list and aggregate performance metrics.
 * Uses compounded returns to track realistic portfolio growth.
 * Use {@link #fromTrades} to compute metrics from a list of trades.
 */
public record BacktestResult(
        List<Trade> trades,
        int totalTrades,
        int winCount,
        int lossCount,
        double winRate,
        double totalReturnPercent,
        double averageReturnPercent,
        double bestTradePercent,
        double worstTradePercent,
        double maxDrawdownPercent,
        double averageHoldingDays,
        long startTimestamp,
        long endTimestamp,
        double startingCapital,
        double finalCapital
) {

    /**
     * Computes aggregate metrics (win rate, drawdown, averages) from the given trades.
     * Returns are compounded: capital *= (1 + trade.returnPercent / 100) for each trade.
     */
    public static BacktestResult fromTrades(List<Trade> trades, long startTimestamp, long endTimestamp,
                                            double startingCapital) {
        if (trades.isEmpty()) {
            return new BacktestResult(
                    trades, 0, 0, 0, 0.0,
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    startTimestamp, endTimestamp,
                    startingCapital, startingCapital
            );
        }

        int totalTrades = trades.size();
        int winCount = (int) trades.stream().filter(t -> t.returnPercent() > 0).count();
        int lossCount = totalTrades - winCount;
        double winRate = (double) winCount / totalTrades * 100.0;

        // Compound returns
        double capital = startingCapital;
        for (Trade trade : trades) {
            capital *= (1.0 + trade.returnPercent() / 100.0);
        }
        double finalCapital = capital;
        double totalReturnPercent = (finalCapital - startingCapital) / startingCapital * 100.0;
        double averageReturnPercent = totalReturnPercent / totalTrades;

        double bestTradePercent = trades.stream().mapToDouble(Trade::returnPercent).max().orElse(0.0);
        double worstTradePercent = trades.stream().mapToDouble(Trade::returnPercent).min().orElse(0.0);

        double maxDrawdownPercent = calculateMaxDrawdown(trades, startingCapital);

        double averageHoldingDays = trades.stream().mapToInt(Trade::holdingDays).average().orElse(0.0);

        return new BacktestResult(
                trades, totalTrades, winCount, lossCount, winRate,
                totalReturnPercent, averageReturnPercent,
                bestTradePercent, worstTradePercent,
                maxDrawdownPercent, averageHoldingDays,
                startTimestamp, endTimestamp,
                startingCapital, finalCapital
        );
    }

    private static double calculateMaxDrawdown(List<Trade> trades, double startingCapital) {
        double capital = startingCapital;
        double peak = capital;
        double maxDrawdown = 0.0;

        for (Trade trade : trades) {
            capital *= (1.0 + trade.returnPercent() / 100.0);
            if (capital > peak) {
                peak = capital;
            }
            double drawdown = (peak - capital) / peak * 100.0;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
            }
        }

        return maxDrawdown;
    }
}
