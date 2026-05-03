package com.securechat.securemessaging.service;

import com.securechat.securemessaging.model.Peer;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PeerService {

    private final Map<String, Peer> peers = new HashMap<>();

    public void registerPeer(Peer peer) {
        peer.setTrusted(false); // default untrusted
        peers.put(peer.getDeviceId(), peer);
    }

    public void trustPeer(String deviceId) {
        Peer p = peers.get(deviceId);
        if (p != null) p.setTrusted(true);
    }

    public boolean isTrusted(String deviceId) {
        return peers.containsKey(deviceId) && peers.get(deviceId).isTrusted();
    }

    public Collection<Peer> getAllPeers() {
        return peers.values();
    }
}