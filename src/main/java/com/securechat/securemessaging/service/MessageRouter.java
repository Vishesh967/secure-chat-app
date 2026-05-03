package com.securechat.securemessaging.service;

import com.securechat.securemessaging.model.TransportType;
import org.springframework.stereotype.Service;

import java.net.InetAddress;

@Service
public class MessageRouter {

    public TransportType decideRoute() {
        if (isInternetAvailable()) return TransportType.INTERNET;
        if (isLanAvailable()) return TransportType.LAN;
        return TransportType.OFFLINE;
    }

    private boolean isInternetAvailable() {
        try {
            return InetAddress.getByName("google.com").isReachable(1000);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isLanAvailable() {
        // simple version for now
        return true;
    }
}
