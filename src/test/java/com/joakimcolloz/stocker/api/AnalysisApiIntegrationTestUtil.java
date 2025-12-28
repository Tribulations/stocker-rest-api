package com.joakimcolloz.stocker.api;

import com.joakimcolloz.stocker.api.dao.CandlestickRepository;
import com.joakimcolloz.stocker.api.entity.Candlestick;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class AnalysisApiIntegrationTestUtil {
    public static Candlestick createCandlestick(String dateTime, double open, double high, double low, double close, long volume, String symbol) {
        return new Candlestick(open, high, low, close, volume, humanReadableToTimestamp(dateTime), symbol);
    }

    public static long humanReadableToTimestamp(String humanReadableTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime dateTime = LocalDateTime.parse(humanReadableTime, formatter);
        return dateTime.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    public static void insertHighVolumeTestDataToDb(final CandlestickRepository candlestickRepository) {
        candlestickRepository.save(createCandlestick("2025-11-30 09:00:00", 100, 103, 99,
                102, 5000, "BOL.ST"));

        // BOL.ST - volumes: 1000, 2000, 3000, 6000 (avg = 3000)
        candlestickRepository.save(createCandlestick("2025-12-01 09:00:00", 100, 103, 99,
                102, 1000, "BOL.ST"));
        candlestickRepository.save(createCandlestick("2025-12-02 09:00:00", 100, 103, 99,
                102, 2000, "BOL.ST"));
        candlestickRepository.save(createCandlestick("2025-12-03 09:00:00", 100, 103, 99,
                102, 3000, "BOL.ST"));
        // High volume - 100% above average (6000 vs avg 3000)
        candlestickRepository.save(createCandlestick("2025-12-04 09:00:00", 100, 103, 99,
                102, 6000, "BOL.ST"));

        // ABB.ST - volumes: 5000, 10000 (avg = 7500)
        candlestickRepository.save(createCandlestick("2025-12-05 09:00:00", 100, 103, 99,
                102, 5000, "ABB.ST"));

        // High volume - 33% above average (10000 vs avg 7500)
        candlestickRepository.save(createCandlestick("2025-12-06 09:00:00", 100, 103, 99,
                102, 10000, "ABB.ST"));
    }

    public static void insertAverageVolumeTestDataToDb(CandlestickRepository candlestickRepository) {
        // BOL.ST - volumes: 1000, 2000, 3000 (avg = 2000)
        candlestickRepository.save(createCandlestick("2025-12-01 09:00:00", 100, 103, 99,
                102, 1000, "BOL.ST"));
        candlestickRepository.save(createCandlestick("2025-12-02 09:00:00", 100, 103, 99,
                102, 2000, "BOL.ST"));
        candlestickRepository.save(createCandlestick("2025-12-03 09:00:00", 100, 103, 99,
                102, 3000, "BOL.ST"));

        // ABB.ST - volumes: 5000, 7000 (avg = 6000)
        candlestickRepository.save(createCandlestick("2025-12-04 09:00:00", 100, 103, 99,
                102, 5000, "ABB.ST"));
        candlestickRepository.save(createCandlestick("2025-12-05 09:00:00", 100, 103, 99,
                102, 7000, "ABB.ST"));
    }

    public static void insertHammerTestDataToDb(CandlestickRepository candlestickRepository) {
        // Regular candle (not a hammer)
        candlestickRepository.save(createCandlestick("2025-07-20 09:00:00", 100, 103, 99,
                102, 5000, "BOL.ST"));

        // Hammer pattern
        // Body = |Close - Open| = |100 - 98| = 2
        // Lower shadow = min(Open, Close) - Low = 98 - 90 = 8
        // Upper shadow = High - max(Open, Close) = 100.5 - 100 = 0.5
        candlestickRepository.save(createCandlestick("2025-07-21 09:00:00", 98, 100.5, 90,
                100, 6000, "BOL.ST"));

        // Another hammer for different symbol
        candlestickRepository.save(createCandlestick("2025-07-20 09:00:00", 95, 97.5, 85,
                97, 7000, "ABB.ST"));

        // Not a hammer (balanced shadows)
        candlestickRepository.save(createCandlestick("2025-07-22 09:00:00", 100, 110, 95,
                105, 8000, "BOL.ST"));
    }
}
