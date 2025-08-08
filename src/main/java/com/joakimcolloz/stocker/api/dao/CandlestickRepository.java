package com.joakimcolloz.stocker.api.dao;

import com.joakimcolloz.stocker.api.entity.Candlestick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RestResource;

import java.util.List;

/**
 * Repository interface for {@link Candlestick} entities.
 * <p>
 * Includes a custom query method to find candlesticks by stock symbol.
 * Exposes read-only REST endpoints via Spring Data REST
 * with custom configuration defined in {@link com.joakimcolloz.stocker.api.config.RestRepositoryConfig}.
 *
 * @author Joakim Colloz
 * @version 1.0
 */
public interface CandlestickRepository extends JpaRepository<Candlestick, Integer> {

    /**
     * Retrieves all candlesticks matching the specified stock symbol.
     * <p>
     * Exposed as a REST resource at
     * <code>/api/candlesticks/search/by-symbol?symbol=SYMBOL</code>.
     *
     * @param symbol the stock symbol to filter by
     * @return a list of candlesticks with the given symbol
     */
    @RestResource(path = "by-symbol", rel = "by-symbol")
    List<Candlestick> findBySymbol(@Param("symbol") String symbol);
}
