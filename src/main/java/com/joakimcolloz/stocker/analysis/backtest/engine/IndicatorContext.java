package com.joakimcolloz.stocker.analysis.backtest.engine;

import com.joakimcolloz.stocker.analysis.indicator.MacdCalculator.MacdValues;

import java.util.Map;

public record IndicatorContext(
        Map<Long, Double> rsiValues,
        Map<Long, MacdValues> macdValues,
        double avgVolume
) {
}
