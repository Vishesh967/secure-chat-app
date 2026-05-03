package com.securechat.securemessaging.controller;

import com.securechat.securemessaging.model.Peer;
import com.securechat.securemessaging.service.PeerService;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/peers")
public class PeerController {

    private final PeerService peerService;

    public PeerController(PeerService peerService) {
        this.peerService = peerService;
    }

    @PostMapping("/register")
    public void register(@RequestBody Peer peer) {
        peerService.registerPeer(peer);
    }

    @PostMapping("/trust")
    public void trust(@RequestParam String deviceId) {
        peerService.trustPeer(deviceId);
    }

    @GetMapping
    public Collection<Peer> list() {
        return peerService.getAllPeers();
    }
}