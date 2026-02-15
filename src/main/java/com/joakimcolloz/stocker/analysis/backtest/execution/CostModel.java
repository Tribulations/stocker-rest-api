package com.joakimcolloz.stocker.analysis.backtest.execution;

public interface CostModel {
    double applyEntryPrice(double rawPrice);

    double applyExitPrice(double rawPrice);

    double computeReturnPercent(double entryPrice, double exitPrice);
}
