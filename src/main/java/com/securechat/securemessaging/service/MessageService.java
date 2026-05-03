package com.securechat.securemessaging.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import com.securechat.securemessaging.model.MessageStatus;
import com.securechat.securemessaging.dto.ConversationPreview;
import com.securechat.securemessaging.dto.MessageResponse;
import com.securechat.securemessaging.model.Message;
import com.securechat.securemessaging.repository.MessageRepository;
import com.securechat.securemessaging.security.AESUtil;
import com.securechat.securemessaging.security.HMACUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.securechat.securemessaging.service.PeerService;
import com.securechat.securemessaging.model.TransportType;
import com.securechat.securemessaging.repository.UserRepository;

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
        if (msg != null) {
            msg.setStatus(MessageStatus.DELIVERED);
            messageRepository.save(msg);
        }
    }

    public MessageResponse sendMessage(String sender, String receiver, String content) {

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
            // Encrypt message
            String encrypted = AESUtil.encrypt(content);
            message.setContent(encrypted);

            // Generate unique nonce
            String nonce;
            do {
                nonce = UUID.randomUUID().toString();
            } while (messageRepository.existsByNonce(nonce));

            message.setNonce(nonce);

            // Generate HMAC for integrity
            message.setHmac(HMACUtil.generateHMAC(encrypted, HMAC_KEY));

        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Encryption error");
        }

        // Save to DB
        Message saved = messageRepository.save(message);

        // Send real-time message (WebSocket)
        messagingTemplate.convertAndSend(
                "/topic/messages/" + receiver,
                toResponse(saved, content)   // sending plaintext for UI (correct in your flow)
        );

        return toResponse(saved, content);
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
                messageRepository.save(msg);
            } catch (Exception e) {
                msg.setRetryCount(msg.getRetryCount() + 1);
                messageRepository.save(msg);
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
                .map(this::decryptAndMap)
                .collect(Collectors.toList());
    }
    public List<ConversationPreview> getDmPreviews(String user) {
        return new ArrayList<>(); // temporary stub
    }
    private MessageResponse decryptAndMap(Message msg) {
        try {
            if (msg.getHmac() == null || msg.getNonce() == null) {
                return toResponse(msg, msg.getContent());
            }

            boolean valid = HMACUtil.verifyHMAC(msg.getContent(), HMAC_KEY, msg.getHmac());
            if (!valid) {
                return toResponse(msg, "[integrity check failed]");
            }

            String decrypted = AESUtil.decrypt(msg.getContent());

            return toResponse(msg, decrypted);

        } catch (Exception e) {
            return toResponse(msg, "[decryption error]");
        }
    }

    private MessageResponse toResponse(Message msg, String content) {
        return new MessageResponse(
                msg.getId(), msg.getSender(), msg.getReceiver(),
                content, msg.getTimestamp());
    }
}
