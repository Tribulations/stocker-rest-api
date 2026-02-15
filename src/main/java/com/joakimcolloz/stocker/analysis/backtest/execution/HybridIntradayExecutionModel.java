package com.joakimcolloz.stocker.analysis.backtest.execution;

import com.joakimcolloz.stocker.analysis.backtest.dto.ExecutionConfig;
import com.joakimcolloz.stocker.analysis.backtest.dto.ExecutionMode;
import com.joakimcolloz.stocker.analysis.backtest.dto.Trade;
import com.joakimcolloz.stocker.analysis.backtest.engine.BacktestEngine;

import java.util.List;

public class HybridIntradayExecutionModel implements ExecutionModel {

    private final BacktestEngine engine;

    public HybridIntradayExecutionModel(BacktestEngine engine) {
        this.engine = engine;
    }

    @Override
    public List<Trade> run(BacktestSeriesInput input, CostModel costModel) {
        ExecutionConfig cfg = input.executionConfig();
        ExecutionConfig hybridCfg = new ExecutionConfig(
                ExecutionMode.HYBRID_INTRADAY,
                cfg.intradayInterval(),
                cfg.slippageBps(),
                cfg.spreadBps(),
                cfg.commissionBps(),
                cfg.conservativeFillMode()
        );

        return engine.runForSymbol(
                input.symbol(),
                input.dailyCandles(),
                input.tradingStartTimestamp(),
                input.entryConditions(),
                input.exitRule(),
                input.exitConditions(),
                input.indicators(),
                input.entryRule(),
                hybridCfg,
                input.intradayCandles(),
                costModel
        );
    }
}
