package com.trading.platform.service;

import com.trading.platform.dto.request.AddCanonicalMomentRequest;
import com.trading.platform.model.mongo.CanonicalMoment;
import com.trading.platform.model.mongo.StockDna;
import com.trading.platform.repository.mongo.StockDnaRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;

@Service
public class StockDnaService {

    private final StockDnaRepository stockDnaRepository;
    private final MarketDataService marketDataService;

    public StockDnaService(StockDnaRepository stockDnaRepository, MarketDataService marketDataService) {
        this.stockDnaRepository = stockDnaRepository;
        this.marketDataService = marketDataService;
    }

    public StockDna getTimeline(String symbol) {
        return stockDnaRepository.findBySymbol(symbol.toUpperCase())
                .map(this::sortedByDateDesc)
                .orElseGet(() -> emptyTimeline(symbol));
    }

    public CanonicalMoment addMoment(String symbol, AddCanonicalMomentRequest request, String addedByEmail) {
        StockDna stockDna = stockDnaRepository.findBySymbol(symbol.toUpperCase())
                .orElseGet(() -> {
                    StockDna created = new StockDna();
                    created.setSymbol(symbol.toUpperCase());
                    return created;
                });

        CanonicalMoment moment = new CanonicalMoment();
        moment.setEventDate(request.getEventDate());
        moment.setEventDescription(request.getEventDescription());
        moment.setSource(request.getSource());
        moment.setAddedBy(addedByEmail);

        BigDecimal priceBefore = request.getPriceBefore();
        BigDecimal priceAfter = request.getPriceAfter();

        // Only hit Yahoo if the caller left prices out — an explicitly supplied
        // price always wins, since the user may know something the API doesn't
        // (a price adjusted for a split, a pre-market print, etc.)
        if ((priceBefore == null || priceAfter == null) && request.getEventDate() != null) {
            var window = marketDataService.getPricesAround(symbol.toUpperCase(), request.getEventDate());
            if (window.isPresent()) {
                if (priceBefore == null) priceBefore = window.get().priceBefore();
                if (priceAfter == null) priceAfter = window.get().priceAfter();
            }
        }

        moment.setPriceBefore(priceBefore);
        moment.setPriceAfter(priceAfter);
        moment.setPercentageImpact(
                request.getPercentageImpact() != null
                        ? request.getPercentageImpact()
                        : calculatePercentageImpact(priceBefore, priceAfter)
        );

        stockDna.getCanonicalMoments().add(moment);
        stockDnaRepository.save(stockDna);
        return moment;
    }

    private Double calculatePercentageImpact(BigDecimal before, BigDecimal after) {
        if (before == null || after == null || before.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return after.subtract(before)
                .divide(before, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private StockDna sortedByDateDesc(StockDna stockDna) {
        stockDna.getCanonicalMoments().sort(
                Comparator.comparing(CanonicalMoment::getEventDate, Comparator.nullsLast(Comparator.reverseOrder()))
        );
        return stockDna;
    }

    private StockDna emptyTimeline(String symbol) {
        StockDna empty = new StockDna();
        empty.setSymbol(symbol.toUpperCase());
        return empty;
    }
}