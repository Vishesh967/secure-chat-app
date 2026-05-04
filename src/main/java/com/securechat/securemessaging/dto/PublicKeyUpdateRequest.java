package com.securechat.securemessaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PublicKeyUpdateRequest {

    @NotBlank(message = "Public key is required")
    @Size(max = 12000, message = "Public key too long")
    private String publicKey;

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
}
