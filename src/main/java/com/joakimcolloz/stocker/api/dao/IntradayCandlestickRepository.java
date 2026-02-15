package com.joakimcolloz.stocker.api.dao;

import com.joakimcolloz.stocker.api.entity.IntradayCandlestick;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IntradayCandlestickRepository extends JpaRepository<IntradayCandlestick, Integer> {

    List<IntradayCandlestick> findByIntervalAndTimestampBetweenOrderBySymbolAscTimestampAsc(
            String interval,
            long startTimestamp,
            long endTimestamp
    );

    List<IntradayCandlestick> findBySymbolInAndIntervalAndTimestampBetweenOrderBySymbolAscTimestampAsc(
            List<String> symbols,
            String interval,
            long startTimestamp,
            long endTimestamp
    );
}
