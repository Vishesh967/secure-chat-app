package com.securechat.securemessaging.controller;

import com.securechat.securemessaging.dto.TypingEvent;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class TypingController {

    private final SimpMessagingTemplate messagingTemplate;

    public TypingController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/typing")
    public void typing(TypingEvent event) {
        if (event == null || event.getReceiver() == null || event.getReceiver().isBlank()) {
            return;
        }
        messagingTemplate.convertAndSend("/topic/typing/" + event.getReceiver(), event);
    }
}
