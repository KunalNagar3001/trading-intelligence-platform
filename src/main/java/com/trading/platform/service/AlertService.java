package com.trading.platform.service;

import com.trading.platform.dto.request.CreateAlertRequest;
import com.trading.platform.model.mysql.PriceAlert;
import com.trading.platform.repository.mysql.AlertRepository;
import com.trading.platform.repository.mysql.UserRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final UserRepository userRepository;

    public AlertService(AlertRepository alertRepository, UserRepository userRepository) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
    }

    public PriceAlert createAlert(String email, CreateAlertRequest request) {
        Long userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();

        PriceAlert alert = new PriceAlert();
        alert.setUserId(userId);
        alert.setSymbol(request.getSymbol().toUpperCase());
        alert.setCondition(request.getCondition());
        alert.setTargetPrice(request.getTargetPrice());
        alert.setActive(true);
        return alertRepository.save(alert);
    }

    public List<PriceAlert> getUserAlerts(String email) {
        Long userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();
        return alertRepository.findByUserId(userId);
    }

    public void deleteAlert(Long alertId) {
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