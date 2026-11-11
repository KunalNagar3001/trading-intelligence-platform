package com.trading.platform.controller;

import com.trading.platform.dto.request.RegisterRequest;
import com.trading.platform.dto.response.RegisterResponse;
import com.trading.platform.model.mysql.User;
import com.trading.platform.service.UserService;
import com.trading.platform.util.JwtUtil;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void registerResponseNeverContainsThePasswordHash() {
        UserService userService = mock(UserService.class);
        User saved = new User();
        saved.setId(5L);
        saved.setName("Asha");
        saved.setEmail("asha@example.com");
        saved.setPasswordHash("$2a$10$thisIsAFakeBcryptHashForTheTest");
        when(userService.registerUser(any(User.class))).thenReturn(saved);
        AuthController controller = new AuthController(userService, mock(JwtUtil.class));

        RegisterRequest request = new RegisterRequest();
        request.setName("Asha");
        request.setEmail("asha@example.com");
        request.setPassword("secret-password");
        RegisterResponse response = controller.register(request);

        assertEquals(5L, response.id());
        assertEquals("asha@example.com", response.email());
        String json = JsonMapper.builder().build().writeValueAsString(response);
        assertFalse(json.toLowerCase().contains("password"), json);
        assertFalse(json.contains("$2a$"), json);
    }
}
