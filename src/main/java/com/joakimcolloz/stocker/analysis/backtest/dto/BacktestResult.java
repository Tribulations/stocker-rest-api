package com.joakimcolloz.stocker.analysis.backtest.dto;

import java.util.List;

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
        long endTimestamp
) {

    public static BacktestResult fromTrades(List<Trade> trades, long startTimestamp, long endTimestamp) {
        if (trades.isEmpty()) {
            return new BacktestResult(
                    trades, 0, 0, 0, 0.0,
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    startTimestamp, endTimestamp
            );
        }

        int totalTrades = trades.size();
        int winCount = (int) trades.stream().filter(t -> t.returnPercent() > 0).count();
        int lossCount = totalTrades - winCount;
        double winRate = (double) winCount / totalTrades * 100.0;

        double totalReturnPercent = trades.stream().mapToDouble(Trade::returnPercent).sum();
        double averageReturnPercent = totalReturnPercent / totalTrades;

        double bestTradePercent = trades.stream().mapToDouble(Trade::returnPercent).max().orElse(0.0);
        double worstTradePercent = trades.stream().mapToDouble(Trade::returnPercent).min().orElse(0.0);

        double maxDrawdownPercent = calculateMaxDrawdown(trades);

        double averageHoldingDays = trades.stream().mapToInt(Trade::holdingDays).average().orElse(0.0);

        return new BacktestResult(
                trades, totalTrades, winCount, lossCount, winRate,
                totalReturnPercent, averageReturnPercent,
                bestTradePercent, worstTradePercent,
                maxDrawdownPercent, averageHoldingDays,
                startTimestamp, endTimestamp
        );
    }

    private static double calculateMaxDrawdown(List<Trade> trades) {
        double cumulativeReturn = 0.0;
        double peak = 0.0;
        double maxDrawdown = 0.0;

        for (Trade trade : trades) {
            cumulativeReturn += trade.returnPercent();
            if (cumulativeReturn > peak) {
                peak = cumulativeReturn;
            }
            double drawdown = peak - cumulativeReturn;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
            }
        }

        return maxDrawdown;
    }
}
