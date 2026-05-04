package com.securechat.securemessaging.controller;

import com.securechat.securemessaging.dto.PublicKeyUpdateRequest;
import com.securechat.securemessaging.dto.UserProfileResponse;
import com.securechat.securemessaging.model.User;
import com.securechat.securemessaging.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{username}/public-key")
    public ResponseEntity<String> getPublicKey(@PathVariable String username) {


        return ResponseEntity.ok(
                publicKeyToString(userService.getPublicKey(username))
        );
    }

    @GetMapping("/{username}")
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable String username) {
        User user = userService.getUser(username);
        return ResponseEntity.ok(new UserProfileResponse(
                user.getUsername(),
                user.getEmail(),
                publicKeyToString(user.getPublicKey())));
    }

    @PostMapping("/me/public-key")
    public ResponseEntity<UserProfileResponse> updateMyPublicKey(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody PublicKeyUpdateRequest request) {
        
        User user = userService.updatePublicKey(username, request.getPublicKey());
        return ResponseEntity.ok(new UserProfileResponse(
                user.getUsername(),
                user.getEmail(),
                publicKeyToString(user.getPublicKey())));
    }

    @GetMapping("/{username}/exists")
    public ResponseEntity<Boolean> userExists(@PathVariable String username) {
        return ResponseEntity.ok(userService.userExists(username));
    }

    private String publicKeyToString(byte[] publicKey) {
        return publicKey == null ? null : Base64.getEncoder().encodeToString(publicKey);
    }

    @GetMapping("/lan-ip")
    public ResponseEntity<String> getLanIp() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) continue;
                java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (address instanceof java.net.Inet4Address) {
                        return ResponseEntity.ok("{\"ip\":\"" + address.getHostAddress() + "\"}");
                    }
                }
            }
        } catch (Exception e) {}
        return ResponseEntity.ok("{\"ip\":\"localhost\"}");
    }
}
