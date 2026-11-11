package com.trading.platform.websocket;

import com.trading.platform.model.mysql.User;
import com.trading.platform.repository.mysql.UserRepository;
import com.trading.platform.service.WatchlistService;
import com.trading.platform.util.JwtUtil;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Live price stream, per user.
 * <p>
 * Protocol: a browser cannot set an Authorization header on a WebSocket, and a token in the URL would end
 * up in logs, so the client authenticates with its first message:
 * <pre>{"type":"auth","token":"&lt;JWT&gt;"}</pre>
 * On success the server replies {"type":"ready"}. A client that has not authenticated within
 * {@value #AUTH_TIMEOUT_SECONDS} seconds is disconnected (close code 1008), and an unauthenticated client
 * never receives a price. After that, each price {"symbol":"INFY.NS","price":1500.0} is sent only to users
 * who hold that symbol or have an active alert on it.
 */
@Component
public class PriceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PriceWebSocketHandler.class);

    static final long AUTH_TIMEOUT_SECONDS = 5;
    static final long WATCHLIST_REFRESH_MS = 10_000;

    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private final ScheduledExecutorService authTimeouts = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-auth-timeout");
        t.setDaemon(true);
        return t;
    });

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final WatchlistService watchlistService;
    private final ObjectMapper objectMapper;

    public PriceWebSocketHandler(JwtUtil jwtUtil, UserRepository userRepository,
                                 WatchlistService watchlistService, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.watchlistService = watchlistService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Client client = new Client(session);
        clients.put(session.getId(), client);
        authTimeouts.schedule(() -> {
            if (!client.authenticated() && session.isOpen()) {
                close(session, CloseStatus.POLICY_VIOLATION.withReason("Authentication timeout"));
            }
        }, AUTH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        log.debug("WebSocket connected: {} (total {})", session.getId(), clients.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        clients.remove(session.getId());
        log.debug("WebSocket closed: {} (total {})", session.getId(), clients.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Client client = clients.get(session.getId());
        if (client == null) return;

        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            close(session, CloseStatus.BAD_DATA.withReason("Messages must be JSON"));
            return;
        }
        if (!"auth".equals(node.path("type").asText(""))) return; // only auth is understood for now

        String token = node.path("token").asText("");
        if (!jwtUtil.isTokenValid(token)) {
            close(session, CloseStatus.POLICY_VIOLATION.withReason("Invalid or expired token"));
            return;
        }
        Optional<User> user = userRepository.findByEmail(jwtUtil.extractEmail(token));
        if (user.isEmpty()) {
            close(session, CloseStatus.POLICY_VIOLATION.withReason("Unknown user"));
            return;
        }
        client.userId = user.get().getId();
        send(client, "{\"type\":\"ready\"}");
    }

    /** Sends the price to every authenticated client that watches the symbol. */
    public void broadcastPrice(String symbol, double price) {
        String message = "{\"symbol\":\"" + symbol + "\",\"price\":" + price + "}";
        String key = symbol.toUpperCase();
        long now = System.currentTimeMillis();
        for (Client client : clients.values()) {
            try {
                if (client.authenticated() && client.session.isOpen() && client.watches(key, now)) {
                    send(client, message);
                }
            } catch (Exception e) {
                // one client's problem (for example a database error loading its watchlist) must not stop the rest
                log.warn("Could not deliver price to session {}: {}", client.session.getId(), e.getMessage());
            }
        }
    }

    private void send(Client client, String json) {
        try {
            // a WebSocketSession is not safe for concurrent sends
            synchronized (client.session) {
                if (client.session.isOpen()) client.session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.warn("Error sending to session {}: {}", client.session.getId(), e.getMessage());
        }
    }

    private void close(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception e) {
            log.debug("Error closing session {}: {}", session.getId(), e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        authTimeouts.shutdownNow();
    }

    /** One connection: who it belongs to (null until authenticated) and a short-lived copy of their watchlist. */
    private final class Client {
        final WebSocketSession session;
        volatile Long userId;
        private volatile Set<String> symbols = Set.of();
        private volatile long loadedAt;

        Client(WebSocketSession session) {
            this.session = session;
        }

        boolean authenticated() {
            return userId != null;
        }

        boolean watches(String symbol, long now) {
            if (now - loadedAt > WATCHLIST_REFRESH_MS) {
                symbols = watchlistService.symbolsFor(userId);
                loadedAt = now;
            }
            return symbols.contains(symbol);
        }
    }
}
