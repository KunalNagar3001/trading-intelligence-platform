package com.trading.platform.websocket;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Random;

@Component
@EnableScheduling
public class PriceBroadcaster {

    private final PriceWebSocketHandler webSocketHandler;
    private final Random random = new Random();

    // Simulated base prices
    private double reliancePrice = 2400.0;
    private double tcsPrice = 3500.0;

    public PriceBroadcaster(PriceWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Scheduled(fixedRate = 3000) // runs every 3 seconds
    public void broadcastPrices() {
        // Simulate small random price movement (±0.5%)
        reliancePrice = reliancePrice + (random.nextDouble() - 0.5) * reliancePrice * 0.005;
        tcsPrice = tcsPrice + (random.nextDouble() - 0.5) * tcsPrice * 0.005;

        webSocketHandler.broadcastPrice("RELIANCE", Math.round(reliancePrice * 100.0) / 100.0);
        webSocketHandler.broadcastPrice("TCS", Math.round(tcsPrice * 100.0) / 100.0);
    }
}