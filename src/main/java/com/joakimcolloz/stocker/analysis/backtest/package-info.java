/**
 * Backtesting engine for evaluating trading strategies against historical candlestick data.
 *
 * <p>Walks price bars chronologically, entering positions when filter conditions
 * (RSI, MACD, HIGH_VOLUME) are met and exiting on stop-loss, take-profit,
 * max holding days, or end of data. Returns a trade list with performance metrics.
 *
 * @see com.joakimcolloz.stocker.analysis.backtest.controller.BacktestController
 * @see com.joakimcolloz.stocker.analysis.backtest.engine.BacktestEngine
 */
package com.joakimcolloz.stocker.analysis.backtest;
