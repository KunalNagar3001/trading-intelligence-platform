package com.trading.platform.controller;

import com.trading.platform.dto.request.RegisterRequest;
import com.trading.platform.dto.request.LoginRequest;
import com.trading.platform.model.mysql.User;
import com.trading.platform.service.UserService;
import org.springframework.web.bind.annotation.*;
import com.trading.platform.util.JwtUtil;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil JwtUtil;

    public AuthController(UserService userService, JwtUtil JwtUtil) {
        this.userService = userService;
        this.JwtUtil = JwtUtil;
    }

    @PostMapping("/register")
    public User register(@RequestBody RegisterRequest request) {
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(request.getPassword()); // plain text for now — we'll hash this soon

        return userService.registerUser(user);
    }

    @PostMapping("/login")
    public String login(@RequestBody LoginRequest request) {
        String email = userService.loginUser(request.getEmail(), request.getPassword());
        return JwtUtil.generateToken(email);
    }
}