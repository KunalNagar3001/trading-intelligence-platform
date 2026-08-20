package com.trading.platform.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

@Component
public class PriceWebSocketHandler extends TextWebSocketHandler {

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        System.out.println("New WebSocket connection: " + session.getId()
                + " | Total connected: " + sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        System.out.println("WebSocket disconnected: " + session.getId()
                + " | Total connected: " + sessions.size());
    }

    public void broadcastPrice(String symbol, double price) {
        String message = "{\"symbol\":\"" + symbol + "\",\"price\":" + price + "}";
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (Exception e) {
                System.out.println("Error sending to session " + session.getId() + ": " + e.getMessage());
            }
        }
    }
}