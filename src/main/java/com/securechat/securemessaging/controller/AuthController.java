package com.securechat.securemessaging.controller;

import com.securechat.securemessaging.dto.*;
import com.securechat.securemessaging.model.User;
import com.securechat.securemessaging.security.JwtUtil;
import com.securechat.securemessaging.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
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
                "message", "OTP sent to " + request.getEmail(),
                "email", request.getEmail()
        ));
    }

    /** Step 2 — verify OTP */
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {

        User user = userService.verifyOtp(request.getEmail(), request.getOtp());
        String token = jwtUtil.generateToken(user.getUsername());

        return ResponseEntity.ok(
                new AuthResponse(
                        token,
                        user.getUsername(),
                        user.getEmail(),
                        Base64.getEncoder().encodeToString(user.getPublicKey())
                )
        );
    }

    /** Resend OTP */
    @PostMapping("/resend-otp")
    public ResponseEntity<Map<String, String>> resendOtp(
            @Valid @RequestBody ResendOtpRequest request) {

        userService.resendOtp(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "OTP resent"));
    }

    /** Login */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        User user = userService.loginUser(request.getUsername(), request.getPassword());
        String token = jwtUtil.generateToken(user.getUsername());

        return ResponseEntity.ok(
                new AuthResponse(
                        token,
                        user.getUsername(),
                        user.getEmail(),
                        Base64.getEncoder().encodeToString(user.getPublicKey())
                )
        );
    }

    /** Get public key */
    @GetMapping("/public-key/{username}")
    public ResponseEntity<String> getPublicKey(@PathVariable String username) {
        return ResponseEntity.ok(
                Base64.getEncoder().encodeToString(userService.getPublicKey(username))
        );
    }
}