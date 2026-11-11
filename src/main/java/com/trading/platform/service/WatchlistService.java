package com.trading.platform.service;

import com.trading.platform.model.mysql.Holding;
import com.trading.platform.model.mysql.PriceAlert;
import com.trading.platform.repository.mysql.AlertRepository;
import com.trading.platform.repository.mysql.HoldingRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * The symbols one user cares about: everything they hold plus everything they have an active alert on.
 * The WebSocket handler uses this to send each client only its own prices.
 */
@Service
public class WatchlistService {

    private final HoldingRepository holdingRepository;
    private final AlertRepository alertRepository;

    public WatchlistService(HoldingRepository holdingRepository, AlertRepository alertRepository) {
        this.holdingRepository = holdingRepository;
        this.alertRepository = alertRepository;
    }

    public Set<String> symbolsFor(Long userId) {
        Set<String> symbols = new HashSet<>();
        for (Holding holding : holdingRepository.findByUserId(userId)) {
            symbols.add(holding.getSymbol().toUpperCase());
        }
        for (PriceAlert alert : alertRepository.findByUserIdAndActiveTrue(userId)) {
            symbols.add(alert.getSymbol().toUpperCase());
        }
        return symbols;
    }
}
