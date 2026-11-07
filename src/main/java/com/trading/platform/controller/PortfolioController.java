package com.trading.platform.controller;

import com.trading.platform.dto.response.PortfolioSummary;
import com.trading.platform.model.mysql.Holding;
import com.trading.platform.model.mysql.Transaction;
import com.trading.platform.service.PortfolioService;
import org.springframework.security.core.Authentication;
import com.trading.platform.dto.request.BuyRequest;
import com.trading.platform.dto.request.SellRequest;
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

    // Live valuation: profit/loss per holding, portfolio totals and allocation by asset type
    @GetMapping("/summary")
    public PortfolioSummary getSummary(Authentication authentication) {
        return portfolioService.getPortfolioSummary(authentication.getName());
    }

    @GetMapping("/transactions")
    public List<Transaction> getTransactions(Authentication authentication) {
        String email = authentication.getName();
        return portfolioService.getUserTransactions(email);
    }

    @PostMapping("/buy")
    public Holding buyStock(Authentication authentication, @RequestBody BuyRequest request) {
        String email = authentication.getName();
        return portfolioService.buyStock(email, request);
    }

    @PostMapping("/sell")
    public Transaction sellStock(Authentication authentication, @RequestBody SellRequest request) {
        String email = authentication.getName();
        return portfolioService.sellStock(email, request);
    }
}