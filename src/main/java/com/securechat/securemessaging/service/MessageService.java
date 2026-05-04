package com.securechat.securemessaging.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import com.securechat.securemessaging.model.MessageStatus;
import com.securechat.securemessaging.dto.ConversationPreview;
import com.securechat.securemessaging.dto.MessageResponse;
import com.securechat.securemessaging.dto.MessageStatusEvent;
import com.securechat.securemessaging.model.Message;
import com.securechat.securemessaging.repository.MessageRepository;
import com.securechat.securemessaging.security.HMACUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.securechat.securemessaging.model.TransportType;
import com.securechat.securemessaging.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final String HMAC_KEY = "1234567890123456";

    private final MessageRepository messageRepository;
    private final MessageRouter router;
    private final SimpMessagingTemplate messagingTemplate;
    private final PeerService peerService;
    private final UserRepository userRepository;

    public MessageService(MessageRepository messageRepository,
                          MessageRouter router,
                          SimpMessagingTemplate messagingTemplate,
                          PeerService peerService,
                          UserRepository userRepository) {

        this.messageRepository = messageRepository;
        this.router = router;
        this.messagingTemplate = messagingTemplate;
        this.peerService = peerService;
        this.userRepository = userRepository;
    }

    public void markAsDelivered(int messageId) {
        Message msg = messageRepository.findById(messageId).orElse(null);
        if (msg != null && msg.getStatus() != MessageStatus.READ) {
            msg.setStatus(MessageStatus.DELIVERED);
            Message saved = messageRepository.save(msg);
            publishStatus(saved);
        }
    }

    public void markMessagesAsRead(String reader, List<Integer> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }

        Map<String, List<Integer>> bySender = new HashMap<>();
        for (Message msg : messageRepository.findAllById(messageIds)) {
            if (!reader.equals(msg.getReceiver())) {
                continue;
            }
            if (msg.getStatus() == MessageStatus.READ) {
                continue;
            }
            msg.setStatus(MessageStatus.READ);
            Message saved = messageRepository.save(msg);
            bySender.computeIfAbsent(saved.getSender(), ignored -> new ArrayList<>())
                    .add(saved.getId());
        }

        bySender.forEach((sender, ids) ->
                messagingTemplate.convertAndSend(
                        "/topic/message-status/" + sender,
                        new MessageStatusEvent(ids, MessageStatus.READ, 0)));
    }

    public MessageResponse sendMessage(String sender,
                                       String receiver,
                                       String encryptedContent,
                                       String nonce,
                                       String senderPublicKey) {
        if (encryptedContent == null || encryptedContent.isBlank()) {
            throw new RuntimeException("Encrypted message content is required");
        }

        Message message = new Message();
        message.setSender(sender);
        message.setReceiver(receiver);

        // Decide transport (LAN / INTERNET / OFFLINE)
        message.setTransport(router.decideRoute());

        // LAN security check
        if (message.getTransport() == TransportType.LAN) {
            if (!peerService.isTrusted(receiver)) {
                throw new RuntimeException("Untrusted peer");
            }
        }

        // Initial message state
        message.setStatus(MessageStatus.PENDING);
        message.setRetryCount(0);

        // Ensure receiver exists
        userRepository.findByUsername(receiver)
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            message.setContent(encryptedContent);
            message.setNonce(resolveNonce(nonce));
            if (senderPublicKey != null && !senderPublicKey.isBlank()) {
                message.setSenderEphemeralPublicKey(senderPublicKey.getBytes(StandardCharsets.UTF_8));
            }

            message.setHmac(HMACUtil.generateHMAC(encryptedContent, HMAC_KEY));

        } catch (Exception e) {
            log.error("Message persistence failed", e);
            throw new RuntimeException("Failed to store encrypted message");
        }

        // Save to DB
        Message saved = messageRepository.save(message);

        // Send real-time message (WebSocket)
        messagingTemplate.convertAndSend(
                "/topic/messages/" + receiver,
                toResponse(saved)
        );

        return toResponse(saved);
    }

    @Scheduled(fixedDelay = 5000)
    public void autoRetry() {
        retryPendingMessages();
    }

    public void retryPendingMessages() {
        List<Message> pending = messageRepository.findByStatus(MessageStatus.PENDING);

        for (Message msg : pending) {
            if (msg.getRetryCount() > 5) continue;

            try {
                if (msg.getStatus() != MessageStatus.DELIVERED) {
                    msg.setStatus(MessageStatus.SENT);
                }
                Message saved = messageRepository.save(msg);
                publishStatus(saved);
            } catch (Exception e) {
                msg.setRetryCount(msg.getRetryCount() + 1);
                Message saved = messageRepository.save(msg);
                publishStatus(saved);
            }
        }
    }

    public List<MessageResponse> getConversation(String user1, String user2) {
        List<Message> side1 = messageRepository
                .findBySenderAndReceiverOrderByTimestampAsc(user1, user2);
        List<Message> side2 = messageRepository
                .findBySenderAndReceiverOrderByTimestampAsc(user2, user1);

        List<Message> all = new ArrayList<>(side1);
        all.addAll(side2);
        all.sort(Comparator.comparing(Message::getTimestamp));

        return all.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
    public List<ConversationPreview> getDmPreviews(String user) {
        Set<String> names = new HashSet<>();
        names.addAll(messageRepository.findReceiversForSender(user));
        names.addAll(messageRepository.findSendersForReceiver(user));
        names.remove(user);

        List<ConversationPreview> previews = new ArrayList<>();
        for (String other : names) {
            List<Message> side1 = messageRepository
                    .findBySenderAndReceiverOrderByTimestampAsc(user, other);
            List<Message> side2 = messageRepository
                    .findBySenderAndReceiverOrderByTimestampAsc(other, user);
            List<Message> all = new ArrayList<>(side1);
            all.addAll(side2);
            all.stream()
                    .max(Comparator.comparing(Message::getTimestamp))
                    .ifPresent(last -> previews.add(
                            ConversationPreview.dm(other, "Encrypted message", last.getTimestamp())));
        }

        previews.sort((a, b) -> {
            if (a.getLastMessageTime() == null && b.getLastMessageTime() == null) return 0;
            if (a.getLastMessageTime() == null) return 1;
            if (b.getLastMessageTime() == null) return -1;
            return b.getLastMessageTime().compareTo(a.getLastMessageTime());
        });
        return previews;
    }

    private String resolveNonce(String requestedNonce) {
        if (requestedNonce != null && !requestedNonce.isBlank()) {
            if (messageRepository.existsByNonce(requestedNonce)) {
                throw new RuntimeException("Duplicate message nonce");
            }
            return requestedNonce;
        }

        String nonce;
        do {
            nonce = UUID.randomUUID().toString();
        } while (messageRepository.existsByNonce(nonce));
        return nonce;
    }

    private void publishStatus(Message msg) {
        messagingTemplate.convertAndSend(
                "/topic/message-status/" + msg.getSender(),
                new MessageStatusEvent(List.of(msg.getId()), msg.getStatus(), msg.getRetryCount()));
    }

    private MessageResponse toResponse(Message msg) {
        String senderPublicKey = msg.getSenderEphemeralPublicKey() == null
                ? null
                : new String(msg.getSenderEphemeralPublicKey(), StandardCharsets.UTF_8);
        return new MessageResponse(
                msg.getId(), msg.getSender(), msg.getReceiver(),
                msg.getContent(), msg.getNonce(), senderPublicKey,
                msg.getStatus(), msg.getRetryCount(), msg.getTimestamp());
    }
}
