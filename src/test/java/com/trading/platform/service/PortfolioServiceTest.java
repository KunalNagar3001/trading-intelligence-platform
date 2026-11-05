package com.trading.platform.service;

import com.trading.platform.dto.request.BuyRequest;
import com.trading.platform.dto.request.SellRequest;
import com.trading.platform.dto.response.HoldingPnl;
import com.trading.platform.dto.response.PortfolioSummary;
import com.trading.platform.exception.HoldingNotFoundException;
import com.trading.platform.model.mysql.Holding;
import com.trading.platform.model.mysql.Transaction;
import com.trading.platform.model.mysql.User;
import com.trading.platform.repository.mysql.HoldingRepository;
import com.trading.platform.repository.mysql.TransactionRepository;
import com.trading.platform.repository.mysql.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class PortfolioServiceTest {

    private static final String EMAIL = "asha@example.com";
    private static final Long USER_ID = 7L;
    private static final String INFY = "INFY.NS";
    private static final String GOLD = "GOLDBEES.NS";

    private HoldingRepository holdingRepository;
    private TransactionRepository transactionRepository;
    private MarketDataService marketDataService;
    private PortfolioService service;

    @BeforeEach
    void setUp() {
        holdingRepository = mock(HoldingRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        marketDataService = mock(MarketDataService.class);
        UserRepository userRepository = mock(UserRepository.class);

        User user = new User();
        user.setId(USER_ID);
        user.setEmail(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        // save() returns whatever it was given, like a real repository
        when(holdingRepository.save(any(Holding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new PortfolioService(holdingRepository, userRepository, transactionRepository, marketDataService);
    }

    // ------------------------------------------------------------------ buy

    @Test
    void buyingANewSymbolCreatesHoldingAtLivePriceNotClientPrice() {
        livePrice(INFY, "1500.00");
        when(holdingRepository.findByUserIdAndSymbol(USER_ID, INFY)).thenReturn(Optional.empty());
        BuyRequest request = buy("infy.ns", 10);
        request.setPrice(new BigDecimal("1.00")); // client-supplied price must be ignored

        Holding result = service.buyStock(EMAIL, request);

        assertEquals(INFY, result.getSymbol());            // normalised to upper case
        assertEquals(10, result.getQuantity());
        assertMoney("1500.00", result.getBuyPrice());

        Transaction tx = savedTransaction();
        assertEquals(Transaction.TransactionType.BUY, tx.getType());
        assertMoney("1500.00", tx.getPrice());
    }

    @Test
    void buyingMoreOfAHeldSymbolAveragesTheBuyPrice() {
        livePrice(INFY, "200.00");
        Holding existing = holding(INFY, "EQUITY", 10, "100.00");
        when(holdingRepository.findByUserIdAndSymbol(USER_ID, INFY)).thenReturn(Optional.of(existing));

        Holding result = service.buyStock(EMAIL, buy(INFY, 10));

        // (10 x 100 + 10 x 200) / 20 = 150
        assertEquals(20, result.getQuantity());
        assertMoney("150.00", result.getBuyPrice());
    }

    @Test
    void buyingWithNonPositiveQuantityIsRejectedBeforeAnythingIsSaved() {
        assertThrows(IllegalArgumentException.class, () -> service.buyStock(EMAIL, buy(INFY, 0)));
        assertThrows(IllegalArgumentException.class, () -> service.buyStock(EMAIL, buy(INFY, -5)));
        assertThrows(IllegalArgumentException.class, () -> service.buyStock(EMAIL, buy(INFY, null)));
        verifyNoInteractions(transactionRepository);
        verify(holdingRepository, never()).save(any());
    }

    @Test
    void buyingWithoutAvailableLivePriceIsRejected() {
        when(marketDataService.getQuote(INFY)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.buyStock(EMAIL, buy(INFY, 1)));
        verifyNoInteractions(transactionRepository);
    }

    // ----------------------------------------------------------------- sell

    @Test
    void sellingMoreThanHeldIsRejectedAndNothingChanges() {
        when(holdingRepository.findByUserIdAndSymbol(USER_ID, INFY))
                .thenReturn(Optional.of(holding(INFY, "EQUITY", 5, "100.00")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.sellStock(EMAIL, sell(INFY, 10)));

        assertTrue(ex.getMessage().contains("you hold 5"));
        verifyNoInteractions(transactionRepository);
        verify(holdingRepository, never()).save(any());
        verify(holdingRepository, never()).delete(any());
    }

    @Test
    void sellingASymbolYouDoNotHoldIsRejected() {
        when(holdingRepository.findByUserIdAndSymbol(USER_ID, INFY)).thenReturn(Optional.empty());

        assertThrows(HoldingNotFoundException.class, () -> service.sellStock(EMAIL, sell(INFY, 1)));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void partialSellReducesQuantityAndKeepsAverageBuyPrice() {
        livePrice(INFY, "120.00");
        Holding existing = holding(INFY, "EQUITY", 10, "100.00");
        when(holdingRepository.findByUserIdAndSymbol(USER_ID, INFY)).thenReturn(Optional.of(existing));

        Transaction tx = service.sellStock(EMAIL, sell(INFY, 4));

        assertEquals(6, existing.getQuantity());
        assertMoney("100.00", existing.getBuyPrice());
        assertEquals(Transaction.TransactionType.SELL, tx.getType());
        assertMoney("120.00", tx.getPrice());
        verify(holdingRepository).save(existing);
        verify(holdingRepository, never()).delete(any());
    }

    @Test
    void sellingEverythingRemovesTheHolding() {
        livePrice(INFY, "120.00");
        Holding existing = holding(INFY, "EQUITY", 10, "100.00");
        when(holdingRepository.findByUserIdAndSymbol(USER_ID, INFY)).thenReturn(Optional.of(existing));

        service.sellStock(EMAIL, sell(INFY, 10));

        verify(holdingRepository).delete(existing);
        verify(holdingRepository, never()).save(any());
    }

    // ------------------------------------------------------------ valuation

    @Test
    void summaryComputesProfitLossTotalsAndAllocation() {
        when(holdingRepository.findByUserId(USER_ID)).thenReturn(List.of(
                holding(INFY, "EQUITY", 10, "1500.00"),     // invested 15000, now 16500 -> +1500 (+10%)
                holding(GOLD, "GOLD_ETF", 100, "50.00")));  // invested  5000, now  4500 ->  -500 (-10%)
        when(marketDataService.getQuotes(anyList()))
                .thenReturn(Map.of(INFY, new BigDecimal("1650.00"), GOLD, new BigDecimal("45.00")));

        PortfolioSummary s = service.getPortfolioSummary(EMAIL);

        HoldingPnl infy = pnl(s, INFY);
        assertMoney("15000.00", infy.investedValue());
        assertMoney("16500.00", infy.currentValue());
        assertMoney("1500.00", infy.profitLoss());
        assertMoney("10.00", infy.profitLossPercent());
        assertFalse(infy.priceStale());

        HoldingPnl gold = pnl(s, GOLD);
        assertMoney("-500.00", gold.profitLoss());
        assertMoney("-10.00", gold.profitLossPercent());

        assertMoney("20000.00", s.totalInvested());
        assertMoney("21000.00", s.totalCurrentValue());
        assertMoney("1000.00", s.totalProfitLoss());
        assertMoney("5.00", s.totalProfitLossPercent());

        // allocation is by current value: 16500 / 21000 and 4500 / 21000
        assertMoney("78.57", s.assetAllocationPercent().get("EQUITY"));
        assertMoney("21.43", s.assetAllocationPercent().get("GOLD_ETF"));
    }

    @Test
    void holdingWithoutLiveQuoteFallsBackToBuyPriceAndIsFlaggedStale() {
        when(holdingRepository.findByUserId(USER_ID))
                .thenReturn(List.of(holding(INFY, "EQUITY", 10, "1500.00")));
        when(marketDataService.getQuotes(anyList())).thenReturn(Map.of());

        PortfolioSummary s = service.getPortfolioSummary(EMAIL);

        HoldingPnl infy = pnl(s, INFY);
        assertTrue(infy.priceStale());
        assertMoney("1500.00", infy.currentPrice());
        assertMoney("0.00", infy.profitLoss());
        assertMoney("0.00", infy.profitLossPercent());
    }

    @Test
    void emptyPortfolioHasZeroTotals() {
        when(holdingRepository.findByUserId(USER_ID)).thenReturn(List.of());

        PortfolioSummary s = service.getPortfolioSummary(EMAIL);

        assertTrue(s.holdings().isEmpty());
        assertMoney("0", s.totalInvested());
        assertMoney("0", s.totalCurrentValue());
        assertTrue(s.assetAllocationPercent().isEmpty());
        verifyNoInteractions(marketDataService);
    }

    // -------------------------------------------------------------- helpers

    private void livePrice(String symbol, String price) {
        when(marketDataService.getQuote(symbol)).thenReturn(Optional.of(new BigDecimal(price)));
    }

    private Transaction savedTransaction() {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        return captor.getValue();
    }

    private static HoldingPnl pnl(PortfolioSummary summary, String symbol) {
        return summary.holdings().stream()
                .filter(h -> h.symbol().equals(symbol))
                .findFirst()
                .orElseThrow();
    }

    /** Compares by value, so 1500, 1500.0 and 1500.00 are all equal. */
    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    private static Holding holding(String symbol, String assetType, int quantity, String buyPrice) {
        Holding h = new Holding();
        h.setUserId(USER_ID);
        h.setSymbol(symbol);
        h.setAssetType(assetType);
        h.setQuantity(quantity);
        h.setBuyPrice(new BigDecimal(buyPrice));
        h.setBuyDate(LocalDate.of(2026, 1, 1));
        return h;
    }

    private static BuyRequest buy(String symbol, Integer quantity) {
        BuyRequest r = new BuyRequest();
        r.setSymbol(symbol);
        r.setAssetType("EQUITY");
        r.setQuantity(quantity);
        return r;
    }

    private static SellRequest sell(String symbol, Integer quantity) {
        SellRequest r = new SellRequest();
        r.setSymbol(symbol);
        r.setQuantity(quantity);
        return r;
    }
}
