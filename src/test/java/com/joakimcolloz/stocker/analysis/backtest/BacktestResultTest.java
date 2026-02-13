package com.joakimcolloz.stocker.analysis.backtest;

import com.joakimcolloz.stocker.analysis.backtest.dto.BacktestResult;
import com.joakimcolloz.stocker.analysis.backtest.dto.Trade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BacktestResult Unit Tests")
class BacktestResultTest {

    private static final long BASE_TS = 1_704_067_200L;
    private static final long DAY = 86_400L;

    private Trade trade(double returnPercent) {
        // Minimal trade with just the return percent that matters for aggregation
        double entryPrice = 100.0;
        double exitPrice = entryPrice * (1.0 + returnPercent / 100.0);
        return new Trade("TEST.ST", BASE_TS, entryPrice, BASE_TS + DAY, exitPrice, "TAKE_PROFIT",
                returnPercent, 1);
    }

    @Nested
    @DisplayName("Compound Returns")
    class CompoundReturns {

        @Test
        @DisplayName("+10% then -10% on 10,000 → finalCapital=9,900, totalReturn=-1.0%")
        void compoundTenPercentUpThenDown() {
            List<Trade> trades = List.of(trade(10.0), trade(-10.0));

            BacktestResult result = BacktestResult.fromTrades(trades, BASE_TS, BASE_TS + 30 * DAY, 10_000.0);

            assertEquals(10_000.0, result.startingCapital());
            assertEquals(9_900.0, result.finalCapital(), 0.001);
            assertEquals(-1.0, result.totalReturnPercent(), 0.001);
            assertEquals(-0.5, result.averageReturnPercent(), 0.001);
        }

        @Test
        @DisplayName("Three consecutive +20% trades compound correctly")
        void threeConsecutiveGains() {
            List<Trade> trades = List.of(trade(20.0), trade(20.0), trade(20.0));

            BacktestResult result = BacktestResult.fromTrades(trades, BASE_TS, BASE_TS + 30 * DAY, 10_000.0);

            // 10000 * 1.2 * 1.2 * 1.2 = 17,280
            assertEquals(17_280.0, result.finalCapital(), 0.001);
            assertEquals(72.8, result.totalReturnPercent(), 0.001);
        }

        @Test
        @DisplayName("Single trade matches simple return")
        void singleTrade() {
            List<Trade> trades = List.of(trade(15.0));

            BacktestResult result = BacktestResult.fromTrades(trades, BASE_TS, BASE_TS + 30 * DAY, 5_000.0);

            assertEquals(5_750.0, result.finalCapital(), 0.001);
            assertEquals(15.0, result.totalReturnPercent(), 0.001);
        }
    }

    @Nested
    @DisplayName("Max Drawdown from Equity Curve")
    class MaxDrawdown {

        @Test
        @DisplayName("Drawdown is percentage of peak, not additive")
        void drawdownAsPercentageOfPeak() {
            // +50% then -20%: capital goes 10000 → 15000 → 12000
            // drawdown = (15000 - 12000) / 15000 * 100 = 20%
            List<Trade> trades = List.of(trade(50.0), trade(-20.0));

            BacktestResult result = BacktestResult.fromTrades(trades, BASE_TS, BASE_TS + 30 * DAY, 10_000.0);

            assertEquals(20.0, result.maxDrawdownPercent(), 0.001);
        }

        @Test
        @DisplayName("Drawdown tracks deepest trough from highest peak")
        void deepestTroughFromPeak() {
            // +100%, -30%, +10%, -40%
            // capital: 10000 → 20000 → 14000 → 15400 → 9240
            // peak after trade 1: 20000
            // after trade 2: dd = (20000-14000)/20000 = 30%
            // peak stays 20000 (15400 < 20000)
            // after trade 4: dd = (20000-9240)/20000 = 53.8%
            List<Trade> trades = List.of(trade(100.0), trade(-30.0), trade(10.0), trade(-40.0));

            BacktestResult result = BacktestResult.fromTrades(trades, BASE_TS, BASE_TS + 30 * DAY, 10_000.0);

            assertEquals(53.8, result.maxDrawdownPercent(), 0.001);
        }

        @Test
        @DisplayName("No drawdown when all trades are winners")
        void noDrawdownAllWinners() {
            List<Trade> trades = List.of(trade(5.0), trade(10.0), trade(3.0));

            BacktestResult result = BacktestResult.fromTrades(trades, BASE_TS, BASE_TS + 30 * DAY, 10_000.0);

            assertEquals(0.0, result.maxDrawdownPercent(), 0.001);
        }
    }

    @Nested
    @DisplayName("Empty Trades")
    class EmptyTrades {

        @Test
        @DisplayName("Empty trades returns startingCapital as finalCapital")
        void emptyTradesReturnsStartingCapital() {
            BacktestResult result = BacktestResult.fromTrades(List.of(), BASE_TS, BASE_TS + 30 * DAY, 10_000.0);

            assertEquals(0, result.totalTrades());
            assertEquals(10_000.0, result.startingCapital());
            assertEquals(10_000.0, result.finalCapital());
            assertEquals(0.0, result.totalReturnPercent());
            assertEquals(0.0, result.maxDrawdownPercent());
        }
    }

    @Nested
    @DisplayName("Win/Loss Counts")
    class WinLossCounts {

        @Test
        @DisplayName("Correctly counts wins and losses")
        void countsWinsAndLosses() {
            List<Trade> trades = List.of(trade(10.0), trade(-5.0), trade(3.0), trade(-2.0));

            BacktestResult result = BacktestResult.fromTrades(trades, BASE_TS, BASE_TS + 30 * DAY, 10_000.0);

            assertEquals(4, result.totalTrades());
            assertEquals(2, result.winCount());
            assertEquals(2, result.lossCount());
            assertEquals(50.0, result.winRate(), 0.001);
            assertEquals(10.0, result.bestTradePercent(), 0.001);
            assertEquals(-5.0, result.worstTradePercent(), 0.001);
        }
    }
}
