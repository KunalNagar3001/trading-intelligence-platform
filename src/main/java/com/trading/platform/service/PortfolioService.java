package com.trading.platform.service;

import com.trading.platform.exception.HoldingNotFoundException;
import com.trading.platform.exception.UserNotFoundException;
import com.trading.platform.model.mysql.Holding;
import com.trading.platform.model.mysql.Instrument;
import com.trading.platform.model.mysql.Transaction;
import com.trading.platform.repository.mysql.HoldingRepository;
import com.trading.platform.repository.mysql.UserRepository;
import com.trading.platform.repository.mysql.TransactionRepository;
import com.trading.platform.dto.request.BuyRequest;
import com.trading.platform.dto.request.SellRequest;
import com.trading.platform.dto.response.HoldingPnl;
import com.trading.platform.dto.response.PortfolioSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PortfolioService {

    private final HoldingRepository holdingRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final MarketDataService marketDataService;

    public PortfolioService(HoldingRepository holdingRepository, UserRepository userRepository, TransactionRepository transactionRepository, MarketDataService marketDataService) {
        this.holdingRepository = holdingRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.marketDataService = marketDataService;
    }

    public List<Holding> getUserPortfolio(String email) {
        Long userId = getUserIdFromEmail(email);
        return holdingRepository.findByUserId(userId);
    }

    public List<Transaction> getUserTransactions(String email) {
        Long userId = getUserIdFromEmail(email);
        return transactionRepository.findByUserId(userId);
    }

    /**
     * Real-time portfolio valuation: pulls one live quote per distinct symbol
     * (not per holding — a user can hold the same symbol only once today, but
     * batching here keeps this correct even if that changes) and computes P&L
     * per holding plus totals and an allocation breakdown by asset type.
     * <p>
     * If Yahoo has no quote for a symbol (rate-limited, delisted, market
     * closed with nothing cached), that one holding's P&L falls back to its
     * buy price and is flagged priceStale — the rest of the portfolio still
     * gets a real valuation instead of the whole request failing.
     */
    public PortfolioSummary getPortfolioSummary(String email) {
        Long userId = getUserIdFromEmail(email);
        List<Holding> holdings = holdingRepository.findByUserId(userId);

        if (holdings.isEmpty()) {
            return new PortfolioSummary(List.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, Map.of());
        }

        List<String> symbols = holdings.stream()
                .map(Holding::getSymbol)
                .distinct()
                .toList();
        Map<String, BigDecimal> livePrices = marketDataService.getQuotes(symbols);

        List<HoldingPnl> holdingPnls = new ArrayList<>();
        Map<String, BigDecimal> currentValueByAssetType = new LinkedHashMap<>();
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalCurrentValue = BigDecimal.ZERO;

        for (Holding h : holdings) {
            BigDecimal quantity = BigDecimal.valueOf(h.getQuantity());
            BigDecimal invested = h.getBuyPrice().multiply(quantity);

            BigDecimal livePrice = livePrices.get(h.getSymbol());
            boolean stale = livePrice == null;
            BigDecimal currentPrice = stale ? h.getBuyPrice() : livePrice;
            BigDecimal currentValue = currentPrice.multiply(quantity);

            BigDecimal profitLoss = currentValue.subtract(invested).setScale(2, RoundingMode.HALF_UP);
            BigDecimal profitLossPercent = percentOf(profitLoss, invested);

            holdingPnls.add(new HoldingPnl(
                    h.getId(),
                    h.getSymbol(),
                    h.getAssetType(),
                    h.getQuantity(),
                    h.getBuyPrice(),
                    currentPrice.setScale(2, RoundingMode.HALF_UP),
                    stale,
                    invested.setScale(2, RoundingMode.HALF_UP),
                    currentValue.setScale(2, RoundingMode.HALF_UP),
                    profitLoss,
                    profitLossPercent
            ));

            totalInvested = totalInvested.add(invested);
            totalCurrentValue = totalCurrentValue.add(currentValue);
            currentValueByAssetType.merge(h.getAssetType(), currentValue, BigDecimal::add);
        }

        BigDecimal totalProfitLoss = totalCurrentValue.subtract(totalInvested).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalProfitLossPercent = percentOf(totalProfitLoss, totalInvested);

        BigDecimal finalTotalCurrentValue = totalCurrentValue;
        Map<String, BigDecimal> allocationPercent = currentValueByAssetType.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> percentOf(e.getValue(), finalTotalCurrentValue),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        return new PortfolioSummary(
                holdingPnls,
                totalInvested.setScale(2, RoundingMode.HALF_UP),
                totalCurrentValue.setScale(2, RoundingMode.HALF_UP),
                totalProfitLoss,
                totalProfitLossPercent,
                allocationPercent
        );
    }

    // Holdings and transactions are only ever changed together, in one DB transaction:
    // buyStock and sellStock are the only write paths, so the two tables cannot drift apart.
    @Transactional
    public Holding buyStock(String email, BuyRequest request) {
        Long userId = getUserIdFromEmail(email);
        String symbol = normalizeSymbol(request.getSymbol());
        requirePositiveQuantity(request.getQuantity());

        BigDecimal livePrice = getLivePrice(symbol);
        LocalDate date = request.getDate() != null ? request.getDate() : LocalDate.now();

        // Step 1: Record the transaction - livePrice, not whatever the client sent
        saveTransaction(userId, symbol, Transaction.TransactionType.BUY, livePrice, request.getQuantity(), date);

        // Step 2: Update or create holding (row is locked while we read it)
        Holding existingHolding = holdingRepository.findByUserIdAndSymbol(userId, symbol).orElse(null);

        if (existingHolding != null) {
            BigDecimal totalCost = existingHolding.getBuyPrice()
                    .multiply(BigDecimal.valueOf(existingHolding.getQuantity()))
                    .add(livePrice.multiply(BigDecimal.valueOf(request.getQuantity())));
            int newQuantity = existingHolding.getQuantity() + request.getQuantity();
            BigDecimal newAvgPrice = totalCost.divide(BigDecimal.valueOf(newQuantity), 2, RoundingMode.HALF_UP);

            existingHolding.setQuantity(newQuantity);
            existingHolding.setBuyPrice(newAvgPrice);
            return holdingRepository.save(existingHolding);
        } else {
            Holding holding = new Holding();
            holding.setUserId(userId);
            holding.setSymbol(symbol);
            holding.setAssetType(request.getAssetType());
            holding.setBuyPrice(livePrice);
            holding.setQuantity(request.getQuantity());
            holding.setBuyDate(date);
            return holdingRepository.save(holding);
        }
    }

    @Transactional
    public Transaction sellStock(String email, SellRequest request) {
        Long userId = getUserIdFromEmail(email);
        String symbol = normalizeSymbol(request.getSymbol());
        requirePositiveQuantity(request.getQuantity());

        Holding holding = holdingRepository.findByUserIdAndSymbol(userId, symbol)
                .orElseThrow(() -> new HoldingNotFoundException("You do not hold " + symbol));

        if (request.getQuantity() > holding.getQuantity()) {
            throw new IllegalArgumentException("Cannot sell " + request.getQuantity()
                    + " shares of " + symbol + "; you hold " + holding.getQuantity());
        }

        BigDecimal livePrice = getLivePrice(symbol);
        LocalDate date = request.getDate() != null ? request.getDate() : LocalDate.now();

        // Step 1: Record the SELL at the live price
        Transaction transaction = saveTransaction(userId, symbol, Transaction.TransactionType.SELL,
                livePrice, request.getQuantity(), date);

        // Step 2: Reduce the holding; selling everything removes the row.
        // The average buy price of the remaining shares does not change on a sell.
        int remaining = holding.getQuantity() - request.getQuantity();
        if (remaining == 0) {
            holdingRepository.delete(holding);
        } else {
            holding.setQuantity(remaining);
            holdingRepository.save(holding);
        }
        return transaction;
    }

    private BigDecimal getLivePrice(String symbol) {
        return marketDataService.getQuote(symbol)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown symbol or no live price available: " + symbol));
    }

    private Transaction saveTransaction(Long userId, String symbol, Transaction.TransactionType type,
                                        BigDecimal price, Integer quantity, LocalDate date) {
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setSymbol(symbol);
        transaction.setType(type);
        transaction.setPrice(price);
        transaction.setQuantity(quantity);
        transaction.setDate(date);
        return transactionRepository.save(transaction);
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        return symbol.trim().toUpperCase();
    }

    private void requirePositiveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be a positive whole number");
        }
    }

    // RECONSTRUCTED: your original percentOf was not in the code I had. Behaviour inferred from its two uses
    // in getPortfolioSummary: (part / whole) x 100, 2 decimals, and 0.00 when whole is zero.
    // If you can restore your original from IntelliJ Local History, use that instead.
    private BigDecimal percentOf(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private Long getUserIdFromEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"))
                .getId();
    }
}