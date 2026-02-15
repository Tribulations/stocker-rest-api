package com.joakimcolloz.stocker.analysis.backtest.execution;

public class BpsCostModel implements CostModel {

    private final double slippageRate;
    private final double halfSpreadRate;
    private final double commissionRate;

    public BpsCostModel(double slippageBps, double spreadBps, double commissionBps) {
        this.slippageRate = slippageBps / 10_000.0;
        this.halfSpreadRate = (spreadBps / 10_000.0) / 2.0;
        this.commissionRate = commissionBps / 10_000.0;
    }

    @Override
    public double applyEntryPrice(double rawPrice) {
        return rawPrice * (1.0 + slippageRate + halfSpreadRate);
    }

    @Override
    public double applyExitPrice(double rawPrice) {
        return rawPrice * (1.0 - slippageRate - halfSpreadRate);
    }

    @Override
    public double computeReturnPercent(double entryPrice, double exitPrice) {
        double costBasis = entryPrice * (1.0 + commissionRate);
        double netProceeds = exitPrice * (1.0 - commissionRate);
        return (netProceeds - costBasis) / costBasis * 100.0;
    }
}
