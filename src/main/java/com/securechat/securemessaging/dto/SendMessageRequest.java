package com.securechat.securemessaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SendMessageRequest {

    @NotBlank(message = "Receiver is required")
    private String receiver;

    @Size(max = 20000, message = "Message too long")
    private String content;

    @Size(max = 20000, message = "Encrypted message too long")
    private String encryptedContent;

    private String nonce;

    @Size(max = 12000, message = "Sender public key too long")
    private String senderPublicKey;

    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getEncryptedContent() {
        return encryptedContent != null ? encryptedContent : content;
    }
    public void setEncryptedContent(String encryptedContent) { this.encryptedContent = encryptedContent; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public String getSenderPublicKey() { return senderPublicKey; }
    public void setSenderPublicKey(String senderPublicKey) { this.senderPublicKey = senderPublicKey; }
}
