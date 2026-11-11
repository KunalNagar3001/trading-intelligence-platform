package com.trading.platform.websocket;

import com.trading.platform.model.mysql.User;
import com.trading.platform.repository.mysql.UserRepository;
import com.trading.platform.service.WatchlistService;
import com.trading.platform.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PriceWebSocketHandlerTest {

    private JwtUtil jwtUtil;
    private UserRepository userRepository;
    private WatchlistService watchlist;
    private PriceWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        userRepository = mock(UserRepository.class);
        watchlist = mock(WatchlistService.class);
        handler = new PriceWebSocketHandler(jwtUtil, userRepository, watchlist, JsonMapper.builder().build());
    }

    @AfterEach
    void tearDown() {
        handler.shutdown();
    }

    @Test
    void unauthenticatedClientNeverReceivesPrices() throws Exception {
        WebSocketSession anonymous = connect("s1");

        handler.broadcastPrice("INFY.NS", 1500.0);

        verify(anonymous, never()).sendMessage(any());
    }

    @Test
    void validTokenGetsReadyThenOnlyItsOwnSymbols() throws Exception {
        WebSocketSession asha = login("s1", "asha-token", "asha@example.com", 1L, Set.of("INFY.NS"));

        handler.broadcastPrice("INFY.NS", 1500.0);
        handler.broadcastPrice("TCS.NS", 3900.0);

        assertEquals(List.of("{\"type\":\"ready\"}", "{\"symbol\":\"INFY.NS\",\"price\":1500.0}"), sent(asha));
    }

    @Test
    void eachUserOnlySeesTheirOwnSymbols() throws Exception {
        WebSocketSession asha = login("s1", "asha-token", "asha@example.com", 1L, Set.of("INFY.NS"));
        WebSocketSession ravi = login("s2", "ravi-token", "ravi@example.com", 2L, Set.of("TCS.NS"));

        handler.broadcastPrice("INFY.NS", 1500.0);
        handler.broadcastPrice("TCS.NS", 3900.0);

        assertEquals(List.of("{\"type\":\"ready\"}", "{\"symbol\":\"INFY.NS\",\"price\":1500.0}"), sent(asha));
        assertEquals(List.of("{\"type\":\"ready\"}", "{\"symbol\":\"TCS.NS\",\"price\":3900.0}"), sent(ravi));
    }

    @Test
    void userWithNoHoldingsOrAlertsGetsNoPrices() throws Exception {
        WebSocketSession newcomer = login("s1", "new-token", "new@example.com", 3L, Set.of());

        handler.broadcastPrice("INFY.NS", 1500.0);

        assertEquals(List.of("{\"type\":\"ready\"}"), sent(newcomer));
    }

    @Test
    void invalidTokenClosesTheConnectionWithPolicyViolation() throws Exception {
        WebSocketSession session = connect("s1");
        when(jwtUtil.isTokenValid("bad")).thenReturn(false);

        handler.handleMessage(session, auth("bad"));

        verify(session).close(argThat(status -> status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()));
        handler.broadcastPrice("INFY.NS", 1500.0);
        verify(session, never()).sendMessage(any());
    }

    @Test
    void validTokenForAnUnknownUserIsRejected() throws Exception {
        WebSocketSession session = connect("s1");
        when(jwtUtil.isTokenValid("ghost-token")).thenReturn(true);
        when(jwtUtil.extractEmail("ghost-token")).thenReturn("ghost@example.com");
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        handler.handleMessage(session, auth("ghost-token"));

        verify(session).close(argThat(status -> status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()));
    }

    @Test
    void nonJsonMessageClosesTheConnection() throws Exception {
        WebSocketSession session = connect("s1");

        handler.handleMessage(session, new TextMessage("not-json"));

        verify(session).close(argThat(status -> status.getCode() == CloseStatus.BAD_DATA.getCode()));
    }

    @Test
    void disconnectedClientsStopReceivingPrices() throws Exception {
        WebSocketSession asha = login("s1", "asha-token", "asha@example.com", 1L, Set.of("INFY.NS"));
        handler.afterConnectionClosed(asha, CloseStatus.NORMAL);

        handler.broadcastPrice("INFY.NS", 1500.0);

        assertEquals(List.of("{\"type\":\"ready\"}"), sent(asha)); // only the earlier ready message
    }

    @Test
    void watchlistIsCachedBetweenPrices() throws Exception {
        login("s1", "asha-token", "asha@example.com", 1L, Set.of("INFY.NS"));

        handler.broadcastPrice("INFY.NS", 1500.0);
        handler.broadcastPrice("INFY.NS", 1501.0);
        handler.broadcastPrice("INFY.NS", 1502.0);

        verify(watchlist, times(1)).symbolsFor(1L);
    }

    // ---- helpers ----

    private WebSocketSession connect(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);
        return session;
    }

    private WebSocketSession login(String id, String token, String email, long userId, Set<String> symbols) throws Exception {
        WebSocketSession session = connect(id);
        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        when(jwtUtil.isTokenValid(token)).thenReturn(true);
        when(jwtUtil.extractEmail(token)).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(watchlist.symbolsFor(userId)).thenReturn(symbols);
        handler.handleMessage(session, auth(token));
        return session;
    }

    private static TextMessage auth(String token) {
        return new TextMessage("{\"type\":\"auth\",\"token\":\"" + token + "\"}");
    }

    private static List<String> sent(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues().stream().map(TextMessage::getPayload).toList();
    }
}
