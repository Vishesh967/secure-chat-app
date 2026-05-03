package com.securechat.securemessaging.controller;

import com.securechat.securemessaging.lan.LanDiscoveryService;
import com.securechat.securemessaging.lan.LanInfoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Exposes LAN discovery information to the frontend.
 *
 * GET /lan/info  — returns the server's LAN IP, port, and full URL
 *                  so the browser can display a QR / shareable link
 *                  for other devices on the same Wi-Fi.
 */
@RestController
@RequestMapping("/lan")
public class LanController {

    private final LanDiscoveryService discoveryService;

    public LanController(LanDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @GetMapping("/info")
    public ResponseEntity<LanInfoResponse> getLanInfo() {
        return ResponseEntity.ok(
                new LanInfoResponse(
                        discoveryService.getLanIp(),
                        discoveryService.getServerPort()));
    }
}
