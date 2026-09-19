package com.trading.platform.kafka.consumer;

import tools.jackson.databind.ObjectMapper;
import com.trading.platform.model.mongo.NewsArticle;
import com.trading.platform.model.mysql.User;
import com.trading.platform.repository.mysql.HoldingRepository;
import com.trading.platform.repository.mysql.UserRepository;
import com.trading.platform.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NewsProcessingConsumer {

    private final ObjectMapper objectMapper;
    private final HoldingRepository holdingRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public NewsProcessingConsumer(ObjectMapper objectMapper,
                                  HoldingRepository holdingRepository,
                                  UserRepository userRepository,
                                  NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.holdingRepository = holdingRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "news-events", groupId = "trading-platform-group")
    public void consumeNewsEvent(String message) {
        NewsArticle article;
        try {
            article = objectMapper.readValue(message, NewsArticle.class);
        } catch (Exception e) {
            System.out.println("Failed to parse news event: " + e.getMessage());
            return;
        }

        for (String symbol : article.getTaggedSymbols()) {
            List<Long> holderIds = holdingRepository.findDistinctUserIdsBySymbol(symbol);
            for (Long userId : holderIds) {
                userRepository.findById(userId).ifPresent(user ->
                        notificationService.sendNewsAlertEmail(user, article, symbol));
            }
        }
    }
}