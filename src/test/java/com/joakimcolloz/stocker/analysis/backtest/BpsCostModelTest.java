package com.joakimcolloz.stocker.analysis.backtest;

import com.joakimcolloz.stocker.analysis.backtest.execution.BpsCostModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("BpsCostModel Unit Tests")
class BpsCostModelTest {

    @Test
    @DisplayName("Entry/exit prices and return% include slippage, spread, and commission")
    void costsAppliedCorrectly() {
        BpsCostModel model = new BpsCostModel(10.0, 20.0, 5.0);

        double entry = model.applyEntryPrice(100.0);
        // entry = 100*(1 + 10bps + 10bps) = 100.2
        assertEquals(100.2, entry, 1e-9);

        double exit = model.applyExitPrice(110.0);
        // exit = 110*(1 - 10bps - 10bps) = 109.78
        assertEquals(109.78, exit, 1e-9);

        double commissionRate = 5.0 / 10_000.0;
        double costBasis = entry * (1.0 + commissionRate);
        double netProceeds = exit * (1.0 - commissionRate);
        double expectedReturn = (netProceeds - costBasis) / costBasis * 100.0;

        assertEquals(expectedReturn, model.computeReturnPercent(entry, exit), 1e-12);
    }
}
