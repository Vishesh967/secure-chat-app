package com.securechat.securemessaging.lan;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UDP broadcast-based LAN discovery service.
 *
 * On startup this service:
 *  1. Broadcasts a "SECURECHAT_HERE:<port>" beacon every 5 seconds on UDP port 47777
 *     so other devices on the same Wi-Fi can find this server automatically.
 *  2. Listens for beacons from other SecureChat instances (future mesh support).
 *
 * Clients (browsers) call GET /lan/info to get the server's LAN IP + port,
 * then connect directly to http://<lanIp>:<port>.
 */
@Service
public class LanDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(LanDiscoveryService.class);

    public static final int  DISCOVERY_PORT    = 47777;
    public static final String BEACON_PREFIX   = "SECURECHAT_HERE:";

    @Value("${server.port:8080}")
    private int serverPort;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newFixedThreadPool(2,
            r -> { Thread t = new Thread(r, "lan-discovery"); t.setDaemon(true); return t; });

    private volatile String lanIp = "127.0.0.1";

    @PostConstruct
    public void start() {
        lanIp = detectLanIp();
        log.info("LAN Discovery starting — LAN IP: {}, server port: {}", lanIp, serverPort);
        running.set(true);
        executor.submit(this::broadcastLoop);
        executor.submit(this::listenLoop);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        executor.shutdownNow();
    }

    public String getLanIp()     { return lanIp; }
    public int    getServerPort(){ return serverPort; }

    // ── Broadcast loop ────────────────────────────────────────

    private void broadcastLoop() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            String beacon  = BEACON_PREFIX + serverPort;
            byte[] payload = beacon.getBytes(StandardCharsets.UTF_8);

            while (running.get()) {
                try {
                    // Broadcast to 255.255.255.255
                    DatagramPacket packet = new DatagramPacket(
                            payload, payload.length,
                            InetAddress.getByName("255.255.255.255"),
                            DISCOVERY_PORT);
                    socket.send(packet);

                    // Also broadcast to subnet .255 address
                    String subnetBroadcast = getSubnetBroadcast(lanIp);
                    if (subnetBroadcast != null && !subnetBroadcast.equals("255.255.255.255")) {
                        DatagramPacket subnetPacket = new DatagramPacket(
                                payload, payload.length,
                                InetAddress.getByName(subnetBroadcast),
                                DISCOVERY_PORT);
                        socket.send(subnetPacket);
                    }
                } catch (Exception e) {
                    log.debug("Broadcast error: {}", e.getMessage());
                }
                Thread.sleep(5000);
            }
        } catch (Exception e) {
            log.warn("LAN broadcast loop stopped: {}", e.getMessage());
        }
    }

    // ── Listen loop ───────────────────────────────────────────

    private void listenLoop() {
        try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT)) {
            socket.setSoTimeout(2000);
            byte[] buf = new byte[256];

            while (running.get()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String msg    = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    String sender = packet.getAddress().getHostAddress();

                    if (msg.startsWith(BEACON_PREFIX) && !sender.equals(lanIp)) {
                        log.debug("Discovered peer SecureChat at {}", sender);
                        // Future: maintain a peer registry for mesh support
                    }
                } catch (SocketTimeoutException ignored) {
                    // Normal — just loop again
                }
            }
        } catch (Exception e) {
            log.warn("LAN listen loop stopped: {}", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private String detectLanIp() {
        try {
            // Prefer the IP that can reach the internet (or LAN gateway)
            try (DatagramSocket s = new DatagramSocket()) {
                s.connect(InetAddress.getByName("8.8.8.8"), 80);
                return s.getLocalAddress().getHostAddress();
            }
        } catch (Exception ignored) {}

        // Fallback: iterate network interfaces
        try {
            for (NetworkInterface ni : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InetAddress addr : java.util.Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}

        return "127.0.0.1";
    }

    private String getSubnetBroadcast(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + "." + parts[2] + ".255";
            }
        } catch (Exception ignored) {}
        return null;
    }
}
