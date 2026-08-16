package com.trading.platform.controller;

import com.trading.platform.model.mysql.Holding;
import com.trading.platform.model.mysql.Transaction;
import com.trading.platform.service.PortfolioService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    public List<Holding> getPortfolio(Authentication authentication) {
        String email = authentication.getName();
        return portfolioService.getUserPortfolio(email);
    }

    @PostMapping("/holdings")
    public Holding addHolding(Authentication authentication, @RequestBody Holding holding) {
        String email = authentication.getName();
        return portfolioService.addHolding(email, holding);
    }

    @DeleteMapping("/holdings/{id}")
    public void deleteHolding(Authentication authentication, @PathVariable Long id) {
        String email = authentication.getName();
        portfolioService.deleteHolding(email, id);
    }

    @GetMapping("/transactions")
    public List<Transaction> getTransactions(Authentication authentication) {
        String email = authentication.getName();
        return portfolioService.getUserTransactions(email);
    }

    @PostMapping("/transactions")
    public Transaction addTransaction(Authentication authentication, @RequestBody Transaction transaction) {
        String email = authentication.getName();
        return portfolioService.addTransaction(email, transaction);
    }
}