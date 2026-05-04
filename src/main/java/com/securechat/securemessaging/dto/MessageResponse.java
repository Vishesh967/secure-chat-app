package com.securechat.securemessaging.dto;

import java.time.LocalDateTime;
import com.securechat.securemessaging.model.MessageStatus;

public class MessageResponse {

    private int id;
    private String sender;
    private String receiver;
    private String content;
    private String encryptedContent;
    private String nonce;
    private String senderPublicKey;
    private MessageStatus status;
    private int retryCount;
    private LocalDateTime timestamp;

    public MessageResponse(int id, String sender, String receiver,
                           String content, LocalDateTime timestamp) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.encryptedContent = content;
        this.timestamp = timestamp;
    }

    public MessageResponse(int id, String sender, String receiver,
                           String content, String nonce, String senderPublicKey,
                           MessageStatus status, int retryCount,
                           LocalDateTime timestamp) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.encryptedContent = content;
        this.nonce = nonce;
        this.senderPublicKey = senderPublicKey;
        this.status = status;
        this.retryCount = retryCount;
        this.timestamp = timestamp;
    }

    public int getId() { return id; }
    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public String getContent() { return content; }
    public String getEncryptedContent() { return encryptedContent; }
    public String getNonce() { return nonce; }
    public String getSenderPublicKey() { return senderPublicKey; }
    public MessageStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
