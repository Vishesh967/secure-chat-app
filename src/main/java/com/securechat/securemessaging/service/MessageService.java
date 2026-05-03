package com.securechat.securemessaging.service;

import com.securechat.securemessaging.dto.ConversationPreview;
import com.securechat.securemessaging.dto.MessageResponse;
import com.securechat.securemessaging.model.Message;
import com.securechat.securemessaging.repository.MessageRepository;
import com.securechat.securemessaging.security.AESUtil;
import com.securechat.securemessaging.security.HMACUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final String HMAC_KEY = "1234567890123456";

    private final MessageRepository messageRepository;

    public MessageService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    /**
     * Encrypts the content, generates a nonce and HMAC, then persists the message.
     */
    public MessageResponse sendMessage(String sender, String receiver, String content) {
        Message message = new Message();
        message.setSender(sender);
        message.setReceiver(receiver);

        try {
            String encrypted = AESUtil.encrypt(content);
            message.setContent(encrypted);

            String nonce;
            do { nonce = UUID.randomUUID().toString(); }
            while (messageRepository.existsByNonce(nonce));
            message.setNonce(nonce);

            message.setHmac(HMACUtil.generateHMAC(encrypted, HMAC_KEY));
        } catch (Exception e) {
            log.error("Encryption failed for sender={}", sender, e);
            throw new RuntimeException("Failed to send message — encryption error");
        }

        Message saved = messageRepository.save(message);
        return toResponse(saved, content);
    }

    /**
     * Returns the full conversation between two users, decrypted and sorted by time.
     */
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

    /**
     * Returns DM conversation previews for the sidebar — one entry per partner,
     * sorted by most recent message. Works for both sender and receiver sides,
     * so User B sees User A in their sidebar even if User A sent first.
     */
    public List<ConversationPreview> getDmPreviews(String username) {
        // Collect all unique partners from both directions
        List<String> sent     = messageRepository.findReceiversForSender(username);
        List<String> received = messageRepository.findSendersForReceiver(username);

        // Merge and deduplicate
        java.util.Set<String> partnerSet = new java.util.LinkedHashSet<>();
        partnerSet.addAll(sent);
        partnerSet.addAll(received);

        return partnerSet.stream()
                .map(partner -> {
                    String lastMsg = "";
                    java.time.LocalDateTime lastTime = null;

                    var latest = messageRepository.findLatestBetween(username, partner);
                    if (latest.isPresent()) {
                        lastTime = latest.get().getTimestamp();
                        try {
                            Message m = latest.get();
                            if (m.getHmac() != null && m.getNonce() != null) {
                                boolean valid = HMACUtil.verifyHMAC(m.getContent(), HMAC_KEY, m.getHmac());
                                if (valid) {
                                    String dec = AESUtil.decrypt(m.getContent());
                                    lastMsg = dec.length() > 60 ? dec.substring(0, 60) + "…" : dec;
                                }
                            } else {
                                lastMsg = m.getContent() != null ? m.getContent() : "";
                            }
                        } catch (Exception ignored) {}
                    }

                    return ConversationPreview.dm(partner, lastMsg, lastTime);
                })
                .sorted(Comparator.comparing(
                        ConversationPreview::getLastMessageTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private MessageResponse decryptAndMap(Message msg) {
        if (msg.getHmac() == null || msg.getNonce() == null) {
            return toResponse(msg, msg.getContent());
        }
        try {
            boolean valid = HMACUtil.verifyHMAC(msg.getContent(), HMAC_KEY, msg.getHmac());
            if (!valid) {
                log.warn("HMAC verification failed for message id={}", msg.getId());
                return toResponse(msg, "[integrity check failed]");
            }
            return toResponse(msg, AESUtil.decrypt(msg.getContent()));
        } catch (Exception e) {
            log.error("Decryption failed for message id={}", msg.getId(), e);
            return toResponse(msg, "[decryption error]");
        }
    }

    private MessageResponse toResponse(Message msg, String content) {
        return new MessageResponse(
                msg.getId(), msg.getSender(), msg.getReceiver(),
                content, msg.getTimestamp());
    }
}
