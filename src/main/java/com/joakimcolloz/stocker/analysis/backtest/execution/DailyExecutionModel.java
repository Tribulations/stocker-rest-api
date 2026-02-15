package com.joakimcolloz.stocker.analysis.backtest.execution;

import com.joakimcolloz.stocker.analysis.backtest.dto.ExecutionConfig;
import com.joakimcolloz.stocker.analysis.backtest.dto.ExecutionMode;
import com.joakimcolloz.stocker.analysis.backtest.dto.Trade;
import com.joakimcolloz.stocker.analysis.backtest.engine.BacktestEngine;

import java.util.List;

public class DailyExecutionModel implements ExecutionModel {

    private final BacktestEngine engine;

    public DailyExecutionModel(BacktestEngine engine) {
        this.engine = engine;
    }

    @Override
    public List<Trade> run(BacktestSeriesInput input, CostModel costModel) {
        ExecutionConfig cfg = input.executionConfig();
        ExecutionConfig dailyCfg = new ExecutionConfig(
                ExecutionMode.DAILY_ONLY,
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
                dailyCfg,
                List.of(),
                costModel
        );
    }
}
