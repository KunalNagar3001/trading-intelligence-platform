package com.trading.platform.dto.request;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BuyRequest {
    private String symbol;
    private String assetType;
    private BigDecimal price;
    private Integer quantity;
    private LocalDate date;
}