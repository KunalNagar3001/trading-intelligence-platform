package com.trading.platform.service;

import com.trading.platform.repository.mongo.PriceHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PriceHistoryServiceTest {

    private PriceHistoryRepository repository;
    private PriceHistoryService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(PriceHistoryRepository.class);
        service = new PriceHistoryService(repository, mock(RedisTemplate.class));
    }

    @Test
    void symbolIsUpperCasedAndLimitIsPassedThrough() {
        service.getPriceHistory("infy.ns", 50);
        assertEquals(50, pageSizeUsedFor("INFY.NS"));
    }

    @Test
    void hugeLimitIsCappedAt500() {
        service.getPriceHistory("INFY.NS", 100_000);
        assertEquals(500, pageSizeUsedFor("INFY.NS"));
    }

    @Test
    void zeroOrNegativeLimitBecomesOne() {
        service.getPriceHistory("INFY.NS", 0);
        assertEquals(1, pageSizeUsedFor("INFY.NS"));
    }

    private int pageSizeUsedFor(String symbol) {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findBySymbolOrderByTimestampDesc(eq(symbol), captor.capture());
        return captor.getValue().getPageSize();
    }
}
