package com.trading.platform.controller;

import com.trading.platform.dto.request.AddCanonicalMomentRequest;
import com.trading.platform.dto.response.NewsCandidate;
import com.trading.platform.model.mongo.CanonicalMoment;
import com.trading.platform.model.mongo.StockDna;
import com.trading.platform.service.StockDnaCandidateService;
import com.trading.platform.service.StockDnaService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dna")
public class StockDnaController {

    private final StockDnaService stockDnaService;
    private final StockDnaCandidateService candidateService;

    public StockDnaController(StockDnaService stockDnaService, StockDnaCandidateService candidateService) {
        this.stockDnaService = stockDnaService;
        this.candidateService = candidateService;
    }

    @GetMapping("/{symbol}")
    public StockDna getTimeline(@PathVariable String symbol) {
        return stockDnaService.getTimeline(symbol);
    }

    @PostMapping("/{symbol}/moments")
    public CanonicalMoment addMoment(Authentication authentication,
                                     @PathVariable String symbol,
                                     @RequestBody AddCanonicalMomentRequest request) {
        return stockDnaService.addMoment(symbol, request, authentication.getName());
    }

    // GET /api/dna/{symbol}/candidates?yearsBack=5
    // Suggestions only -- nothing here is saved. Review and POST the real ones as moments.
    @GetMapping("/{symbol}/candidates")
    public List<NewsCandidate> getCandidates(@PathVariable String symbol,
                                             @RequestParam(defaultValue = "5") int yearsBack) {
        return candidateService.findCandidates(symbol, yearsBack);
    }
}