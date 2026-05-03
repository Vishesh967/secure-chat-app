package com.securechat.securemessaging.lan;

/**
 * Returned by GET /lan/info — tells the browser how to reach this
 * SecureChat server from other devices on the same Wi-Fi.
 */
public class LanInfoResponse {

    private final String lanIp;
    private final int    port;
    private final String lanUrl;
    private final int    discoveryPort;
    private final String mode;

    public LanInfoResponse(String lanIp, int port) {
        this.lanIp         = lanIp;
        this.port          = port;
        this.lanUrl        = "http://" + lanIp + ":" + port;
        this.discoveryPort = LanDiscoveryService.DISCOVERY_PORT;
        this.mode          = lanIp.startsWith("127.") ? "LOCALHOST" : "LAN";
    }

    public String getLanIp()        { return lanIp; }
    public int    getPort()         { return port; }
    public String getLanUrl()       { return lanUrl; }
    public int    getDiscoveryPort(){ return discoveryPort; }
    public String getMode()         { return mode; }
}
