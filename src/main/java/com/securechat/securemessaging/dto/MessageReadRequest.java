package com.securechat.securemessaging.dto;

import java.util.ArrayList;
import java.util.List;

public class MessageReadRequest {

    private List<Integer> messageIds = new ArrayList<>();

    public List<Integer> getMessageIds() { return messageIds; }
    public void setMessageIds(List<Integer> messageIds) { this.messageIds = messageIds; }
}
