package com.trading.platform.service;

import com.trading.platform.dto.request.CreateAlertRequest;
import com.trading.platform.exception.AlertNotFoundException;
import com.trading.platform.exception.UnauthorizedActionException;
import com.trading.platform.exception.UserNotFoundException;
import com.trading.platform.model.mysql.PriceAlert;
import com.trading.platform.repository.mysql.AlertRepository;
import com.trading.platform.repository.mysql.UserRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import com.trading.platform.service.MarketDataService;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final InstrumentService instrumentService;
    private final MarketDataService marketDataService;

    @Value("${app.market-data.mode:simulated}")
    private String marketDataMode;

    public AlertService(AlertRepository alertRepository, UserRepository userRepository,
                        RedisTemplate<String, String> redisTemplate, InstrumentService instrumentService, MarketDataService marketDataService) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.instrumentService = instrumentService;
        this.marketDataService = marketDataService;
    }

    public PriceAlert createAlert(String email, CreateAlertRequest request) {
        Long userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"))
                .getId();

        String symbol = request.getSymbol().toUpperCase();

        if ("live".equals(marketDataMode)) {
            marketDataService.getQuote(symbol)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown symbol or no live price available: " + symbol));
        } else {
            instrumentService.requireValidSymbol(symbol);
        }
        PriceAlert.AlertType type = request.getAlertType() != null
                ? request.getAlertType()
                : PriceAlert.AlertType.TARGET_PRICE;

        PriceAlert alert = new PriceAlert();
        alert.setUserId(userId);
        alert.setSymbol(symbol);
        alert.setAlertType(type);
        alert.setActive(true);

        switch (type) {
            case TARGET_PRICE -> {
                if (request.getTargetPrice() == null || request.getCondition() == null) {
                    throw new IllegalArgumentException("targetPrice and condition are required for TARGET_PRICE alerts");
                }
                alert.setCondition(request.getCondition());
                alert.setTargetPrice(request.getTargetPrice());
            }
            case STOP_LOSS -> {
                if (request.getTargetPrice() == null) {
                    throw new IllegalArgumentException("targetPrice is required for STOP_LOSS alerts");
                }
                // A stop-loss only ever makes sense as a downside trigger, regardless of what's passed
                alert.setCondition(PriceAlert.AlertCondition.BELOW);
                alert.setTargetPrice(request.getTargetPrice());
            }
            case PERCENTAGE_CHANGE -> {
                if (request.getPercentageThreshold() == null || request.getCondition() == null) {
                    throw new IllegalArgumentException("percentageThreshold and condition are required for PERCENTAGE_CHANGE alerts");
                }
                if (request.getPercentageThreshold() <= 0) {
                    throw new IllegalArgumentException("percentageThreshold must be positive");
                }
                String cached = redisTemplate.opsForValue().get("price:" + symbol);
                if (cached == null) {
                    throw new IllegalArgumentException(
                            "No current price available for " + symbol + " yet — cannot set a percentage alert");
                }
                alert.setCondition(request.getCondition());
                alert.setPercentageThreshold(request.getPercentageThreshold());
                alert.setBasePrice(new BigDecimal(cached));
            }
        }

        return alertRepository.save(alert);
    }

    public List<PriceAlert> getUserAlerts(String email) {
        Long userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"))
                .getId();
        return alertRepository.findByUserIdAndActiveTrue(userId);
    }

    public void deleteAlert(String email, Long alertId) {
        Long userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"))
                .getId();

        PriceAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new AlertNotFoundException("Alert not found"));

        if (!alert.getUserId().equals(userId)) {
            throw new UnauthorizedActionException("You do not have permission to delete this alert");
        }

        alertRepository.deleteById(alertId);
    }

    public List<PriceAlert> getActiveAlertsForSymbol(String symbol) {
        return alertRepository.findBySymbolAndActiveTrue(symbol);
    }

    public void deactivateAlert(PriceAlert alert) {
        alert.setActive(false);
        alertRepository.save(alert);
    }
}