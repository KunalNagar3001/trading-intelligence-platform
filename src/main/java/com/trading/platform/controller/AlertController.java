package com.trading.platform.controller;

import com.trading.platform.dto.request.CreateAlertRequest;
import com.trading.platform.model.mysql.PriceAlert;
import com.trading.platform.service.AlertService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping
    public PriceAlert createAlert(Authentication authentication,
                                  @RequestBody CreateAlertRequest request) {
        return alertService.createAlert(authentication.getName(), request);
    }

    @GetMapping
    public List<PriceAlert> getUserAlerts(Authentication authentication) {
        return alertService.getUserAlerts(authentication.getName());
    }

    @DeleteMapping("/{id}")
    public void deleteAlert(Authentication authentication, @PathVariable Long id) {
        alertService.deleteAlert(authentication.getName(), id);
    }
}