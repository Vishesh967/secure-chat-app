package com.securechat.securemessaging.controller;

import com.securechat.securemessaging.dto.*;
import com.securechat.securemessaging.model.User;
import com.securechat.securemessaging.security.JwtUtil;
import com.securechat.securemessaging.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil     jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil     = jwtUtil;
    }

    /** Step 1 — create unverified account and send OTP */
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @Valid @RequestBody RegisterRequest request) {

        userService.registerUser(
                request.getUsername(),
                request.getEmail(),
                request.getPassword());

        return ResponseEntity.ok(Map.of(
                "message", "OTP sent to " + request.getEmail() + ". Please verify to activate your account.",
                "email",   request.getEmail()
        ));
    }

    /** Step 2 — verify OTP and activate account */
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {

        User user = userService.verifyOtp(request.getEmail(), request.getOtp());
        String token = jwtUtil.generateToken(user.getUsername());
        return ResponseEntity.ok(
                new AuthResponse(token, user.getUsername(), user.getEmail(), user.getPublicKey()));
    }

    /** Resend OTP to the same email */
    @PostMapping("/resend-otp")
    public ResponseEntity<Map<String, String>> resendOtp(
            @Valid @RequestBody ResendOtpRequest request) {

        userService.resendOtp(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "A new OTP has been sent to " + request.getEmail()));
    }

    /** Login — only verified accounts succeed */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        User user = userService.loginUser(request.getUsername(), request.getPassword());
        String token = jwtUtil.generateToken(user.getUsername());
        return ResponseEntity.ok(
                new AuthResponse(token, user.getUsername(), user.getEmail(), user.getPublicKey()));
    }

    @GetMapping("/public-key/{username}")
    public ResponseEntity<String> getPublicKey(@PathVariable String username) {
        return ResponseEntity.ok(userService.getPublicKey(username));
    }
}
