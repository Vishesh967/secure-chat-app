package com.securechat.securemessaging.dto;

public class AuthResponse {

    private String token;
    private String username;
    private String email;
    private String publicKey;

    public AuthResponse(String token, String username, String email, String publicKey) {
        this.token     = token;
        this.username  = username;
        this.email     = email;
        this.publicKey = publicKey;
    }

    public String getToken()     { return token; }
    public String getUsername()  { return username; }
    public String getEmail()     { return email; }
    public String getPublicKey() { return publicKey; }
}
