package com.securechat.securemessaging.controller;

import com.securechat.securemessaging.dto.ConversationPreview;
import com.securechat.securemessaging.dto.MessageResponse;
import com.securechat.securemessaging.dto.SendMessageRequest;
import com.securechat.securemessaging.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /** Send a DM. Sender is taken from the JWT — never from the request body. */
    @PostMapping("/send")
    public ResponseEntity<MessageResponse> sendMessage(
            @AuthenticationPrincipal String sender,
            @Valid @RequestBody SendMessageRequest request) {

        return ResponseEntity.ok(
                messageService.sendMessage(sender, request.getReceiver(), request.getContent()));
    }

    /** Full conversation between the authenticated user and user2. */
    @GetMapping("/chat")
    public ResponseEntity<List<MessageResponse>> getChat(
            @AuthenticationPrincipal String currentUser,
            @RequestParam String user2) {

        return ResponseEntity.ok(messageService.getConversation(currentUser, user2));
    }

    /** Sidebar DM previews — one entry per conversation partner, sorted by recency. */
    @GetMapping("/previews")
    public ResponseEntity<List<ConversationPreview>> getPreviews(
            @AuthenticationPrincipal String currentUser) {

        return ResponseEntity.ok(messageService.getDmPreviews(currentUser));
    }
}
