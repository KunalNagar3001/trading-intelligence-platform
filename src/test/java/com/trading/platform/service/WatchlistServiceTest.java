package com.trading.platform.service;

import com.trading.platform.model.mysql.Holding;
import com.trading.platform.model.mysql.PriceAlert;
import com.trading.platform.repository.mysql.AlertRepository;
import com.trading.platform.repository.mysql.HoldingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WatchlistServiceTest {

    private HoldingRepository holdings;
    private AlertRepository alerts;
    private WatchlistService service;

    @BeforeEach
    void setUp() {
        holdings = mock(HoldingRepository.class);
        alerts = mock(AlertRepository.class);
        service = new WatchlistService(holdings, alerts);
    }

    @Test
    void combinesHeldSymbolsAndActiveAlertSymbolsWithoutDuplicates() {
        when(holdings.findByUserId(1L)).thenReturn(List.of(holding("INFY.NS"), holding("TCS.NS")));
        when(alerts.findByUserIdAndActiveTrue(1L)).thenReturn(List.of(alert("TCS.NS"), alert("SBIN.NS")));

        assertEquals(Set.of("INFY.NS", "TCS.NS", "SBIN.NS"), service.symbolsFor(1L));
    }

    @Test
    void symbolsAreUpperCased() {
        when(holdings.findByUserId(1L)).thenReturn(List.of(holding("infy.ns")));
        when(alerts.findByUserIdAndActiveTrue(1L)).thenReturn(List.of());

        assertEquals(Set.of("INFY.NS"), service.symbolsFor(1L));
    }

    @Test
    void userWithNothingWatchedGetsAnEmptySet() {
        when(holdings.findByUserId(2L)).thenReturn(List.of());
        when(alerts.findByUserIdAndActiveTrue(2L)).thenReturn(List.of());

        assertEquals(Set.of(), service.symbolsFor(2L));
    }

    private static Holding holding(String symbol) {
        Holding h = new Holding();
        h.setSymbol(symbol);
        return h;
    }

    private static PriceAlert alert(String symbol) {
        PriceAlert a = new PriceAlert();
        a.setSymbol(symbol);
        return a;
    }
}
