package com.securechat.securemessaging.dto;

import com.securechat.securemessaging.model.MessageStatus;
import java.util.List;

public class MessageStatusEvent {

    private List<Integer> messageIds;
    private MessageStatus status;
    private int retryCount;

    public MessageStatusEvent(List<Integer> messageIds, MessageStatus status, int retryCount) {
        this.messageIds = messageIds;
        this.status = status;
        this.retryCount = retryCount;
    }

    public List<Integer> getMessageIds() { return messageIds; }
    public MessageStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
}
