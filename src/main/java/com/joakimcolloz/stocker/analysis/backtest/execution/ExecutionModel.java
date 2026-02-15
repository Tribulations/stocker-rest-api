package com.joakimcolloz.stocker.analysis.backtest.execution;

import com.joakimcolloz.stocker.analysis.backtest.dto.Trade;

import java.util.List;

public interface ExecutionModel {
    List<Trade> run(BacktestSeriesInput input, CostModel costModel);
}
