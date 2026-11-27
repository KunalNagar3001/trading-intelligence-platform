package com.trading.platform.service;

import com.trading.platform.model.mysql.Holding;
import com.trading.platform.model.mysql.PriceAlert;
import com.trading.platform.repository.mysql.AlertRepository;
import com.trading.platform.repository.mysql.HoldingRepository;
import com.trading.platform.repository.mysql.WatchlistItemRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * The symbols one user cares about: everything they hold, everything they have an active alert on,
 * and everything on any of their watchlists. The WebSocket handler uses this to send each client
 * only its own prices. Creating and editing watchlists lives in WatchlistManagementService.
 */
@Service
public class WatchlistService {

    private final HoldingRepository holdingRepository;
    private final AlertRepository alertRepository;
    private final WatchlistItemRepository watchlistItemRepository;

    public WatchlistService(HoldingRepository holdingRepository, AlertRepository alertRepository,
                            WatchlistItemRepository watchlistItemRepository) {
        this.holdingRepository = holdingRepository;
        this.alertRepository = alertRepository;
        this.watchlistItemRepository = watchlistItemRepository;
    }

    public Set<String> symbolsFor(Long userId) {
        Set<String> symbols = new HashSet<>();
        for (Holding holding : holdingRepository.findByUserId(userId)) {
            symbols.add(holding.getSymbol().toUpperCase());
        }
        for (PriceAlert alert : alertRepository.findByUserIdAndActiveTrue(userId)) {
            symbols.add(alert.getSymbol().toUpperCase());
        }
        for (String symbol : watchlistItemRepository.findDistinctSymbolsByUserId(userId)) {
            symbols.add(symbol.toUpperCase());
        }
        return symbols;
    }
}
