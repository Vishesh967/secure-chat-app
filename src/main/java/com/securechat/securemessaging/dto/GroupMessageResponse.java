package com.securechat.securemessaging.dto;

import java.time.LocalDateTime;

public class GroupMessageResponse {

    private Long id;
    private Long groupId;
    private String sender;
    private String content;
    private String encryptedContent;
    private String nonce;
    private LocalDateTime timestamp;

    public GroupMessageResponse(Long id, Long groupId, String sender,
                                String content, LocalDateTime timestamp) {
        this.id        = id;
        this.groupId   = groupId;
        this.sender    = sender;
        this.content   = content;
        this.encryptedContent = content;
        this.timestamp = timestamp;
    }

    public GroupMessageResponse(Long id, Long groupId, String sender,
                                String content, String nonce,
                                LocalDateTime timestamp) {
        this.id        = id;
        this.groupId   = groupId;
        this.sender    = sender;
        this.content   = content;
        this.encryptedContent = content;
        this.nonce     = nonce;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public String getSender() { return sender; }
    public String getContent() { return content; }
    public String getEncryptedContent() { return encryptedContent; }
    public String getNonce() { return nonce; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
