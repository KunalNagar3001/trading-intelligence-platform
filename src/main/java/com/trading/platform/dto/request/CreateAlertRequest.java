package com.trading.platform.dto.request;

import com.trading.platform.model.mysql.PriceAlert;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateAlertRequest {
    private String symbol;
    private PriceAlert.AlertType alertType;   // defaults to TARGET_PRICE if omitted, for backward compatibility
    private PriceAlert.AlertCondition condition;
    private BigDecimal targetPrice;           // required for TARGET_PRICE and STOP_LOSS
    private Double percentageThreshold;       // required for PERCENTAGE_CHANGE, e.g. 5 = 5%
}