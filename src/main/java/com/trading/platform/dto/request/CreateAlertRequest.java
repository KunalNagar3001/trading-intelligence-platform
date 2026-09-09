package com.trading.platform.dto.request;

import com.trading.platform.model.mysql.PriceAlert;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateAlertRequest {
    private String symbol;
    private PriceAlert.AlertCondition condition;
    private BigDecimal targetPrice;
}