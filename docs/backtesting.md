# Backtesting Framework

The backtesting framework evaluates trading strategies against historical candlestick (OHLCV) data. You define entry conditions, exit rules, and optionally an entry mode and starting capital. The engine walks through bars chronologically, simulates trades, and returns a list of completed trades with compounded performance metrics.

## Architecture

```
BacktestController          (REST endpoint)
  |
BacktestService             (orchestration: fetch candles, compute indicators, run engine)
  |
BacktestEngine              (pure bar-by-bar simulation logic, no Spring dependencies)
  |
BacktestResult.fromTrades() (aggregate metrics with compounded returns)
```

**BacktestService** fetches candles from the database with an extended lookback window (so indicators like RSI-14 have enough history to stabilise before the trading window starts), pre-computes all needed indicators per symbol into an `IndicatorContext`, then delegates to the engine. After collecting trades from all symbols it hands them to `BacktestResult.fromTrades()` for aggregation.

**BacktestEngine** is a pure-logic class with no Spring dependencies, making it easy to unit test. It processes one symbol at a time and enforces one position at a time per symbol.

## Engine Loop: Three-State Machine

On each bar the engine runs three steps in order:

```
for each bar:
    1. CHECK EXITS     — if a position is open, evaluate stop-loss / take-profit / max-holding-days
    2. FILL PENDING    — if a pending order exists (and no position), try to fill it
       2a. INTRABAR EXIT — if a fill just created a position, check exits on the same bar
    3. CHECK ENTRY     — if flat and no pending order, evaluate entry conditions
```

This ordering means:
- Exits are always evaluated before new entries, preventing overlapping positions.
- A pending order is filled on the bar *after* the signal, never on the signal bar itself.
- After filling, the same bar's high/low are checked against stop-loss and take-profit (intrabar exit), since the position was entered at the bar's open or limit price and the bar's range still applies.
- While a pending order or open position exists, no new entry signals are evaluated.
- At end of data, open positions are force-closed at the last bar's close (`EXIT_REASON: END_OF_DATA`). Unfilled pending orders are silently dropped.

## Entry Conditions

All conditions in the `entryConditions` array must be satisfied simultaneously on the same bar for a signal to fire (logical AND). Available condition types:

### RSI

The Relative Strength Index measures momentum on a 0-100 scale. Values below 30 are typically considered oversold (potential buy signal) and above 70 overbought.

```json
{ "type": "RSI", "params": { "threshold": 30, "condition": "below" } }
```

| Param | Type | Description |
|---|---|---|
| `threshold` | number | RSI level to compare against |
| `condition` | string | `"above"` or `"below"` |

Computed with a 14-period Wilder smoothing by default.

### MACD

The Moving Average Convergence Divergence tracks the relationship between two exponential moving averages. The engine computes a MACD line, signal line, and histogram for each bar.

```json
{ "type": "MACD", "params": { "condition": "crossover_above", "fastPeriod": 12, "slowPeriod": 26, "signalPeriod": 9 } }
```

| Param | Type | Default | Description |
|---|---|---|---|
| `condition` | string | *required* | See conditions below |
| `fastPeriod` | int | 12 | Fast EMA period |
| `slowPeriod` | int | 26 | Slow EMA period |
| `signalPeriod` | int | 9 | Signal EMA period |

**Conditions:** `above_zero`, `below_zero`, `histogram_positive`, `histogram_negative`, `crossover_above` (MACD crosses above signal line between previous and current bar), `crossover_below`.

### HIGH_VOLUME

Triggers when a bar's volume exceeds the series average by a configurable percentage.

```json
{ "type": "HIGH_VOLUME", "params": { "percentageAbove": 100 } }
```

| Param | Type | Description |
|---|---|---|
| `percentageAbove` | int | Required excess above average (e.g. 100 = volume must be 2x average) |

### PATTERN

Triggers when a candlestick pattern is detected on a bar.

```json
{ "type": "PATTERN", "params": { "pattern": "HAMMER" } }
```

| Param | Type | Description |
|---|---|---|
| `pattern` | string | Pattern code registered in PatternRegistry (e.g. `"HAMMER"`) |

## Entry Modes

The `entryRule` field controls *how* a position is opened after a signal fires. This prevents the unrealistic assumption of entering at the same price that generated the signal.

### NEXT_BAR_OPEN (default)

Signal fires on bar N. Position opens at bar N+1's opening price. Always fills on the very next bar.

```json
{ "entryMode": "NEXT_BAR_OPEN" }
```

### LIMIT_ORDER

Signal fires on bar N. A limit buy order is placed below the signal bar's close. The order has a time-in-force window; if it doesn't fill within that window, it expires with no trade.

```json
{
  "entryMode": "LIMIT_ORDER",
  "limitOffsetPercent": 2.0,
  "penetrationPercent": 0.5,
  "maxBarsToFill": 3
}
```

| Param | Type | Default | Description |
|---|---|---|---|
| `entryMode` | string | `"NEXT_BAR_OPEN"` | `"NEXT_BAR_OPEN"` or `"LIMIT_ORDER"` |
| `limitOffsetPercent` | number | 0.0 | Limit price = signalClose * (1 - offset/100). A 2% offset on a 100 close gives a 98 limit. |
| `penetrationPercent` | number | 0.0 | Fill requires bar low <= limit * (1 - penetration/100). Adds a confirmation threshold beyond the limit itself. |
| `maxBarsToFill` | int | 1 | Number of bars after the signal to attempt a fill. Order expires if not filled. |

**Fill logic per bar:** the engine checks if `bar.low <= limitPrice * (1 - penetrationPercent / 100)`. If yes, position opens at `limitPrice`. If no, `barsRemaining` is decremented; at zero the order is cancelled.

## Exit Rules

At least one exit rule must be specified. They are checked on every bar while a position is open, in this priority order:

| Rule | Check | Exit Price |
|---|---|---|
| `stopLossPercent` | `bar.low <= entryPrice * (1 - stopLoss/100)` | The computed stop price |
| `takeProfitPercent` | `bar.high >= entryPrice * (1 + takeProfit/100)` | The computed target price |
| `maxHoldingDays` | `(barTimestamp - entryTimestamp) / 86400 >= maxDays` | Bar's close price |

Stop-loss is checked against the bar's low and take-profit against the bar's high. This models intraday price action realistically: a bar might touch your stop level even if it closes above it.

If none of the exit rules trigger by the end of the data range, the position is force-closed at the last bar's close with reason `END_OF_DATA`.

## Compounded Returns

Portfolio returns are compounded, not summed. Starting from `startingCapital` (default 10,000):

```
capital = startingCapital
for each trade:
    capital *= (1 + trade.returnPercent / 100)
finalCapital = capital
totalReturnPercent = (finalCapital - startingCapital) / startingCapital * 100
```

This means two trades of +10% then -10% on 10,000 capital gives 9,900 (not 10,000), because the loss applies to the grown capital. This matches real portfolio behaviour.

**Max drawdown** is computed from the equity curve as a percentage of the peak:

```
drawdown = (peak - current) / peak * 100
```

This tracks how far the portfolio fell from its highest point, which is the standard way drawdown is measured in practice.

## API Reference

### Endpoint

```
POST /api/analysis/backtest
```

### Request

```json
{
  "entryConditions": [
    { "type": "RSI", "params": { "threshold": 30, "condition": "below" } },
    { "type": "PATTERN", "params": { "pattern": "HAMMER" } }
  ],
  "exitRule": {
    "stopLossPercent": 5.0,
    "takeProfitPercent": 10.0,
    "maxHoldingDays": 30
  },
  "startTimestamp": 1704067200,
  "endTimestamp": 1706745600,
  "symbols": ["ABB.ST"],
  "startingCapital": 50000.0,
  "entryRule": {
    "entryMode": "LIMIT_ORDER",
    "limitOffsetPercent": 1.5,
    "penetrationPercent": 0.0,
    "maxBarsToFill": 2
  }
}
```

| Field | Required | Default | Description |
|---|---|---|---|
| `entryConditions` | yes | | Array of filter conditions (all must be met) |
| `exitRule` | yes | | At least one of stopLoss/takeProfit/maxHoldingDays |
| `startTimestamp` | yes | | Trading window start (epoch seconds) |
| `endTimestamp` | yes | | Trading window end (epoch seconds) |
| `symbols` | no | all symbols | Filter to specific tickers |
| `startingCapital` | no | 10000.0 | Initial portfolio value for compounding |
| `entryRule` | no | NEXT_BAR_OPEN | Entry mode configuration |

### Response

```json
{
  "trades": [
    {
      "symbol": "ABB.ST",
      "entryTimestamp": 1704240000,
      "entryPrice": 98.5,
      "exitTimestamp": 1704672000,
      "exitPrice": 108.35,
      "exitReason": "TAKE_PROFIT",
      "returnPercent": 10.0,
      "holdingDays": 5
    },
    {
      "symbol": "ABB.ST",
      "entryTimestamp": 1704844800,
      "entryPrice": 105.0,
      "exitTimestamp": 1705276800,
      "exitPrice": 99.75,
      "exitReason": "STOP_LOSS",
      "returnPercent": -5.0,
      "holdingDays": 5
    }
  ],
  "totalTrades": 2,
  "winCount": 1,
  "lossCount": 1,
  "winRate": 50.0,
  "totalReturnPercent": 4.5,
  "averageReturnPercent": 2.25,
  "bestTradePercent": 10.0,
  "worstTradePercent": -5.0,
  "maxDrawdownPercent": 5.0,
  "averageHoldingDays": 5.0,
  "startTimestamp": 1704067200,
  "endTimestamp": 1706745600,
  "startingCapital": 50000.0,
  "finalCapital": 52250.0
}
```

| Field | Description |
|---|---|
| `trades` | Ordered list of completed trades |
| `totalTrades` | Number of completed trades |
| `winCount` / `lossCount` | Trades with positive / non-positive return |
| `winRate` | `winCount / totalTrades * 100` |
| `totalReturnPercent` | Compounded total return as percentage |
| `averageReturnPercent` | `totalReturnPercent / totalTrades` |
| `bestTradePercent` / `worstTradePercent` | Best and worst individual trade returns |
| `maxDrawdownPercent` | Largest peak-to-trough decline in equity, as % of peak |
| `averageHoldingDays` | Mean holding period across all trades |
| `startingCapital` / `finalCapital` | Portfolio value before and after all trades |

### Exit reasons

| Reason | Meaning |
|---|---|
| `STOP_LOSS` | Bar low breached the stop-loss level |
| `TAKE_PROFIT` | Bar high reached the take-profit level |
| `MAX_HOLDING_DAYS` | Position held longer than the allowed period |
| `END_OF_DATA` | Data range ended with the position still open |

## Source Layout

```
analysis/backtest/
  controller/
    BacktestController.java      REST endpoint
  dto/
    BacktestRequest.java         Request DTO
    BacktestResult.java          Response DTO + aggregation logic
    EntryRule.java               Entry mode configuration
    ExitRule.java                Exit criteria
    Trade.java                   Single completed trade
  engine/
    BacktestEngine.java          Bar-by-bar simulation
    IndicatorContext.java         Pre-computed indicator values
  service/
    BacktestService.java         Orchestration (data fetching, indicator computation)
```

## Further Reading

- **Backtesting fundamentals:**
  Investopedia overview of what backtesting is and common pitfalls (look-ahead bias, survivorship bias, overfitting):
  https://www.investopedia.com/terms/b/backtesting.asp

- **RSI (Relative Strength Index):**
  Original concept by J. Welles Wilder. The Investopedia article covers the formula, interpretation of overbought/oversold levels, and divergence signals:
  https://www.investopedia.com/terms/r/rsi.asp

- **MACD (Moving Average Convergence Divergence):**
  Developed by Gerald Appel. Explains the MACD line, signal line, histogram, and crossover strategies:
  https://www.investopedia.com/terms/m/macd.asp

- **Limit orders and order types:**
  Explains limit orders vs market orders, fill mechanics, and time-in-force concepts (GTC, day orders):
  https://www.investopedia.com/terms/l/limitorder.asp

- **Compounding returns:**
  Why geometric (compounded) returns differ from arithmetic (additive) returns and why it matters for portfolio tracking:
  https://www.investopedia.com/terms/c/compoundreturn.asp

- **Maximum drawdown:**
  Standard risk metric for evaluating strategy performance. Measures worst peak-to-trough decline:
  https://www.investopedia.com/terms/m/maximum-drawdown-mdd.asp

- **Candlestick patterns (Hammer):**
  Visual guide to the hammer pattern and its use as a bullish reversal signal:
  https://www.investopedia.com/terms/h/hammer.asp

- **Look-ahead bias in backtesting:**
  Detailed explanation of why entering at the signal bar's close is unrealistic and how next-bar entry avoids it:
  https://www.investopedia.com/terms/l/lookaheadbias.asp

- **Quantitative backtesting methodology:**
  Ernie Chan's *Quantitative Trading* blog covers practical pitfalls in systematic strategy evaluation:
  http://epchan.blogspot.com/
