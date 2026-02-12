package com.joakimcolloz.stocker.analysis.backtest.dto;

import com.joakimcolloz.stocker.analysis.dto.filter.FilterRequest;
import com.joakimcolloz.stocker.analysis.validation.TimeRangeRequest;
import com.joakimcolloz.stocker.analysis.validation.ValidTimeRange;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
@ValidTimeRange
public class BacktestRequest implements TimeRangeRequest {

    @NotEmpty(message = "At least one entry condition is required")
    private List<FilterRequest> entryConditions;

    @NotNull(message = "Exit rule is required")
    private ExitRule exitRule;

    @Positive
    private long startTimestamp;

    @Positive
    private long endTimestamp;

    private List<String> symbols;
}
