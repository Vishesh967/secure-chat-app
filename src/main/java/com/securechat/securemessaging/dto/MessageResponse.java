package com.securechat.securemessaging.dto;

import java.time.LocalDateTime;

public class MessageResponse {

    private int id;
    private String sender;
    private String receiver;
    private String content;
    private LocalDateTime timestamp;

    public MessageResponse(int id, String sender, String receiver,
                           String content, LocalDateTime timestamp) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.timestamp = timestamp;
    }

    public int getId() { return id; }
    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public String getContent() { return content; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
