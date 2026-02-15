package com.joakimcolloz.stocker.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "stock_prices_intraday", schema = "stock_prices_schema")
public class IntradayCandlestick {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "open")
    private double open;

    @Column(name = "high")
    private double high;

    @Column(name = "low")
    private double low;

    @Column(name = "close")
    private double close;

    @Column(name = "volume")
    private Long volume;

    @Column(name = "timestamp")
    private long timestamp;

    @Column(name = "symbol")
    private String symbol;

    @Column(name = "interval")
    private String interval;

    public IntradayCandlestick() {
    }
}
