package com.trading.platform.dto.request;

import lombok.Data;
import java.time.LocalDate;

@Data
public class SellRequest {
    private String symbol;
    private Integer quantity;
    private LocalDate date;
}