package com.trading.platform.service;

import com.trading.platform.exception.HoldingNotFoundException;
import com.trading.platform.exception.UnauthorizedActionException;
import com.trading.platform.exception.UserNotFoundException;
import com.trading.platform.model.mysql.Holding;
import com.trading.platform.model.mysql.Instrument;
import com.trading.platform.model.mysql.Transaction;
import com.trading.platform.repository.mysql.HoldingRepository;
import com.trading.platform.repository.mysql.UserRepository;
import com.trading.platform.repository.mysql.TransactionRepository;
import com.trading.platform.dto.request.BuyRequest;
import org.springframework.stereotype.Service;
import java.util.List;
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

    public Holding addHolding(String email, Holding holding) {
        Long userId = getUserIdFromEmail(email);
        holding.setUserId(userId);
        return holdingRepository.save(holding);
    }

    public void deleteHolding(String email, Long holdingId) {
        Long userId = getUserIdFromEmail(email);

        Holding holding = holdingRepository.findById(holdingId)
                .orElseThrow(() -> new HoldingNotFoundException("Holding not found"));

        if (!holding.getUserId().equals(userId)) {
            throw new UnauthorizedActionException("You do not have permission to delete this holding");
        }

        holdingRepository.deleteById(holdingId);
    }

    public List<Transaction> getUserTransactions(String email) {
        Long userId = getUserIdFromEmail(email);
        return transactionRepository.findByUserId(userId);
    }

    public Transaction addTransaction(String email, Transaction transaction) {
        Long userId = getUserIdFromEmail(email);
        transaction.setUserId(userId);
        return transactionRepository.save(transaction);
    }

    public Holding buyStock(String email, BuyRequest request) {
        Long userId = getUserIdFromEmail(email);

        BigDecimal livePrice = marketDataService.getQuote(request.getSymbol())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown symbol or no live price available: " + request.getSymbol()));

        // Step 1: Record the transaction — livePrice, not whatever the client sent
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setSymbol(request.getSymbol());
        transaction.setType(Transaction.TransactionType.BUY);
        transaction.setPrice(livePrice);
        transaction.setQuantity(request.getQuantity());
        transaction.setDate(request.getDate() != null ? request.getDate() : java.time.LocalDate.now());
        transactionRepository.save(transaction);

        // Step 2: Update or create holding
        List<Holding> existingHoldings = holdingRepository.findByUserId(userId);
        Holding existingHolding = existingHoldings.stream()
                .filter(h -> h.getSymbol().equals(request.getSymbol()))
                .findFirst()
                .orElse(null);

        if (existingHolding != null) {
            BigDecimal totalCost = existingHolding.getBuyPrice()
                    .multiply(BigDecimal.valueOf(existingHolding.getQuantity()))
                    .add(livePrice.multiply(BigDecimal.valueOf(request.getQuantity())));
            int newQuantity = existingHolding.getQuantity() + request.getQuantity();
            BigDecimal newAvgPrice = totalCost.divide(BigDecimal.valueOf(newQuantity), 2, java.math.RoundingMode.HALF_UP);

            existingHolding.setQuantity(newQuantity);
            existingHolding.setBuyPrice(newAvgPrice);
            return holdingRepository.save(existingHolding);
        } else {
            Holding holding = new Holding();
            holding.setUserId(userId);
            holding.setSymbol(request.getSymbol());
            holding.setAssetType(request.getAssetType());
            holding.setBuyPrice(livePrice);
            holding.setQuantity(request.getQuantity());
            holding.setBuyDate(request.getDate() != null ? request.getDate() : java.time.LocalDate.now());
            return holdingRepository.save(holding);
        }
    }

    private Long getUserIdFromEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"))
                .getId();
    }
}