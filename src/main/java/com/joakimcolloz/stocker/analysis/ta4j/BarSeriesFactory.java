package com.joakimcolloz.stocker.analysis.ta4j;

import com.joakimcolloz.stocker.api.entity.Candlestick;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class BarSeriesFactory {
	private BarSeriesFactory() {
	}

	public static BarSeries fromCandlesticks(List<Candlestick> sortedCandles, String seriesName) {
		BarSeries series = new BaseBarSeriesBuilder().withName(seriesName).build();
		Duration tradingSession = Duration.ofSeconds(30600);

		for (Candlestick candle : sortedCandles) {
			Instant instant = Instant.ofEpochSecond(candle.getTimestamp());
			BaseBar bar = new BaseBar(
					tradingSession,
					instant,
					instant.plus(tradingSession),
					DecimalNum.valueOf(candle.getOpen()),
					DecimalNum.valueOf(candle.getHigh()),
					DecimalNum.valueOf(candle.getLow()),
					DecimalNum.valueOf(candle.getClose()),
					DecimalNum.valueOf(candle.getVolume()),
					DecimalNum.valueOf(0),
					0L
			);
			series.addBar(bar);
		}

		return series;
	}
}
