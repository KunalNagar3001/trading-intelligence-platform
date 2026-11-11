package com.trading.platform.controller;

import com.trading.platform.dto.request.LoginRequest;
import com.trading.platform.dto.request.RegisterRequest;
import com.trading.platform.dto.response.RegisterResponse;
import com.trading.platform.model.mysql.User;
import com.trading.platform.service.UserService;
import com.trading.platform.util.JwtUtil;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public RegisterResponse register(@RequestBody RegisterRequest request) {
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(request.getPassword()); // UserService hashes it with BCrypt before saving
        User saved = userService.registerUser(user);
        return new RegisterResponse(saved.getId(), saved.getName(), saved.getEmail());
    }

    @PostMapping("/login")
    public String login(@RequestBody LoginRequest request) {
        String email = userService.loginUser(request.getEmail(), request.getPassword());
        return jwtUtil.generateToken(email);
    }
}
