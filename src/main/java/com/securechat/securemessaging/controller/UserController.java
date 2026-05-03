package com.securechat.securemessaging.controller;

import com.securechat.securemessaging.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{username}/public-key")
    public ResponseEntity<String> getPublicKey(@PathVariable String username) {
        return ResponseEntity.ok(userService.getPublicKey(username));
    }

    @GetMapping("/{username}/exists")
    public ResponseEntity<Boolean> userExists(@PathVariable String username) {
        return ResponseEntity.ok(userService.userExists(username));
    }
}
