package com.trading.platform.service;

import com.trading.platform.model.mysql.Instrument;
import com.trading.platform.repository.mysql.InstrumentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class InstrumentService {

    private final InstrumentRepository instrumentRepository;

    public InstrumentService(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    public List<Instrument> search(String query, int limit) {
        if (query == null || query.trim().isEmpty()) return List.of();
        return instrumentRepository.search(query.trim(), PageRequest.of(0, Math.min(limit, 50)));
    }

    public Instrument requireValidSymbol(String symbol) {
        return instrumentRepository.findBySymbolAndActiveTrue(symbol.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown or inactive symbol: " + symbol));
    }

    public boolean isValidSymbol(String symbol) {
        return instrumentRepository.findBySymbolAndActiveTrue(symbol.toUpperCase()).isPresent();
    }
}