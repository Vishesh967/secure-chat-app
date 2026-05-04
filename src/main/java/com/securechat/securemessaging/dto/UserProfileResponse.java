package com.securechat.securemessaging.dto;

public class UserProfileResponse {

    private String username;
    private String email;
    private String publicKey;

    public UserProfileResponse(String username, String email, String publicKey) {
        this.username = username;
        this.email = email;
        this.publicKey = publicKey;
    }

    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPublicKey() { return publicKey; }
}
