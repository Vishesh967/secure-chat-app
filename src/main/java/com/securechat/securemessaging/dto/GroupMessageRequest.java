package com.securechat.securemessaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class GroupMessageRequest {

    @NotBlank(message = "Message content is required")
    @Size(max = 20000, message = "Message too long")
    private String content;

    private String nonce;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }
}
