package com.trading.platform.kafka.consumer;

import com.trading.platform.model.mongo.PriceHistory;
import com.trading.platform.repository.mongo.PriceHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PriceHistoryConsumerTest {

    private PriceHistoryRepository repository;
    private PriceHistoryConsumer consumer;

    @BeforeEach
    void setUp() {
        repository = mock(PriceHistoryRepository.class);
        consumer = new PriceHistoryConsumer(repository, JsonMapper.builder().build());
    }

    @Test
    void storesEachPriceEventAsASnapshot() {
        consumer.store("{\"eventId\":\"e-1\",\"symbol\":\"INFY.NS\",\"price\":1500.456,\"timestamp\":1700000000000}");

        ArgumentCaptor<PriceHistory> captor = ArgumentCaptor.forClass(PriceHistory.class);
        verify(repository).save(captor.capture());
        PriceHistory saved = captor.getValue();

        assertEquals("INFY.NS", saved.getSymbol());
        assertEquals(0, new BigDecimal("1500.46").compareTo(saved.getClose()));
        assertEquals(LocalDateTime.ofInstant(Instant.ofEpochMilli(1700000000000L), ZoneId.of("Asia/Kolkata")),
                saved.getTimestamp());
    }

    @Test
    void malformedMessageThrowsIllegalArgumentSoItGoesToDeadLetterTopic() {
        assertThrows(IllegalArgumentException.class, () -> consumer.store("not-json"));
        verifyNoInteractions(repository);
    }
}
