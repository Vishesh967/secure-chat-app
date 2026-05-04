package com.securechat.securemessaging.service;

import com.securechat.securemessaging.dto.ImageMessageResponse;
import com.securechat.securemessaging.model.GroupImageMessage;
import com.securechat.securemessaging.model.ImageMessage;
import com.securechat.securemessaging.repository.GroupImageMessageRepository;
import com.securechat.securemessaging.repository.GroupMemberRepository;
import com.securechat.securemessaging.repository.ImageMessageRepository;
import com.securechat.securemessaging.security.HMACUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.securechat.securemessaging.model.MessageStatus;
import com.securechat.securemessaging.dto.MessageStatusEvent;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);
    private static final String HMAC_KEY = "1234567890123456";
    private static final long MAX_BYTES = 16 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    private final ImageMessageRepository imageRepo;
    private final GroupImageMessageRepository groupImageRepo;
    private final GroupMemberRepository memberRepo;
    private final SimpMessagingTemplate messagingTemplate;

    public ImageService(ImageMessageRepository imageRepo,
                        GroupImageMessageRepository groupImageRepo,
                        GroupMemberRepository memberRepo,
                        SimpMessagingTemplate messagingTemplate) {
        this.imageRepo = imageRepo;
        this.groupImageRepo = groupImageRepo;
        this.memberRepo = memberRepo;
        this.messagingTemplate = messagingTemplate;
    }

    public ImageMessageResponse sendDmImage(String sender,
                                            String receiver,
                                            MultipartFile file) {
        validateFile(file);

        ImageMessage msg = new ImageMessage();
        msg.setSender(sender);
        msg.setReceiver(receiver);
        msg.setImageName(sanitizeFilename(file.getOriginalFilename()));
        msg.setImageType(file.getContentType());
        msg.setImageSize(file.getSize());

        try {
            String encryptedBlob = Base64.getEncoder().encodeToString(file.getBytes());
            msg.setEncryptedData(encryptedBlob);
            msg.setNonce(resolveDmNonce());
            msg.setHmac(HMACUtil.generateHMAC(encryptedBlob, HMAC_KEY));
        } catch (Exception e) {
            log.error("DM image persistence failed sender={} receiver={}", sender, receiver, e);
            throw new RuntimeException("Image storage failed");
        }

        return toResponse(imageRepo.save(msg));
    }

    public ImageMessageResponse sendGroupImage(String sender,
                                               Long groupId,
                                               MultipartFile file) {
        if (!memberRepo.existsByGroupIdAndUsername(groupId, sender)) {
            throw new RuntimeException("You are not a member of this group");
        }

        validateFile(file);

        GroupImageMessage msg = new GroupImageMessage();
        msg.setSender(sender);
        msg.setGroupId(groupId);
        msg.setImageName(sanitizeFilename(file.getOriginalFilename()));
        msg.setImageType(file.getContentType());
        msg.setImageSize(file.getSize());

        try {
            String encryptedBlob = Base64.getEncoder().encodeToString(file.getBytes());
            msg.setEncryptedData(encryptedBlob);
            msg.setNonce(resolveGroupNonce());
            msg.setHmac(HMACUtil.generateHMAC(encryptedBlob, HMAC_KEY));
        } catch (Exception e) {
            log.error("Group image persistence failed group={} sender={}", groupId, sender, e);
            throw new RuntimeException("Image storage failed");
        }

        return toGroupResponse(groupImageRepo.save(msg));
    }

    public List<ImageMessageResponse> getDmImages(String user1, String user2) {
        return imageRepo.findConversationImages(user1, user2)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<ImageMessageResponse> getGroupImages(Long groupId, String requestingUser) {
        if (!memberRepo.existsByGroupIdAndUsername(groupId, requestingUser)) {
            throw new RuntimeException("You are not a member of this group");
        }
        return groupImageRepo.findByGroupIdOrderByTimestampAsc(groupId)
                .stream()
                .map(this::toGroupResponse)
                .collect(Collectors.toList());
    }

    public ImageMessageResponse getDmImage(Long imageId, String requestingUser) {
        ImageMessage msg = imageRepo.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Image not found"));

        if (!msg.getSender().equals(requestingUser) && !msg.getReceiver().equals(requestingUser)) {
            throw new RuntimeException("Access denied");
        }

        return toResponse(msg);
    }

    public ImageMessageResponse getGroupImage(Long imageId, String requestingUser) {
        GroupImageMessage msg = groupImageRepo.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Image not found"));

        if (!memberRepo.existsByGroupIdAndUsername(msg.getGroupId(), requestingUser)) {
            throw new RuntimeException("Access denied");
        }

        return toGroupResponse(msg);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("No file provided");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new RuntimeException("Encrypted image payload is too large");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new RuntimeException("Unsupported file type - use JPG, PNG, or WEBP");
        }
    }

    public void markImageAsDelivered(Long messageId) {
        ImageMessage msg = imageRepo.findById(messageId).orElse(null);
        if (msg != null && msg.getStatus() != MessageStatus.READ) {
            msg.setStatus(MessageStatus.DELIVERED);
            ImageMessage saved = imageRepo.save(msg);
            publishStatus(saved);
        }
    }

    public void markImagesAsRead(String reader, List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return;

        List<ImageMessage> messages = imageRepo.findAllById(messageIds)
                .stream()
                .filter(m -> m.getReceiver().equals(reader) && m.getStatus() != MessageStatus.READ)
                .map(m -> {
                    m.setStatus(MessageStatus.READ);
                    return m;
                })
                .collect(Collectors.toList());

        if (messages.isEmpty()) return;

        List<ImageMessage> saved = imageRepo.saveAll(messages);

        Map<String, List<ImageMessage>> bySender = saved.stream()
                .collect(Collectors.groupingBy(ImageMessage::getSender));

        bySender.forEach((sender, senderMessages) -> {
            List<Integer> intIds = senderMessages.stream()
                    .map(m -> m.getId().intValue())
                    .collect(Collectors.toList());
            MessageStatusEvent event = new MessageStatusEvent(intIds, MessageStatus.READ, 0);
            messagingTemplate.convertAndSend("/topic/message-status/" + sender, event);
        });
    }

    private void publishStatus(ImageMessage msg) {
        MessageStatusEvent event = new MessageStatusEvent(
                List.of(msg.getId().intValue()),
                msg.getStatus(),
                msg.getRetryCount()
        );
        messagingTemplate.convertAndSend("/topic/message-status/" + msg.getSender(), event);
    }

    private String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "image";
        }
        return original.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private String resolveDmNonce() {
        String nonce;
        do {
            nonce = UUID.randomUUID().toString();
        } while (imageRepo.existsByNonce(nonce));
        return nonce;
    }

    private String resolveGroupNonce() {
        String nonce;
        do {
            nonce = UUID.randomUUID().toString();
        } while (groupImageRepo.existsByNonce(nonce));
        return nonce;
    }

    private ImageMessageResponse toResponse(ImageMessage msg) {
        ImageMessageResponse response = new ImageMessageResponse();
        response.setId(msg.getId());
        response.setSender(msg.getSender());
        response.setReceiver(msg.getReceiver());
        response.setImageName(msg.getImageName());
        response.setImageType(msg.getImageType());
        response.setImageSize(msg.getImageSize());
        response.setTimestamp(msg.getTimestamp());
        response.setEncryptedData(msg.getEncryptedData());
        response.setStatus(msg.getStatus().name());
        response.setRetryCount(msg.getRetryCount());
        return response;
    }

    private ImageMessageResponse toGroupResponse(GroupImageMessage msg) {
        ImageMessageResponse response = new ImageMessageResponse();
        response.setId(msg.getId());
        response.setSender(msg.getSender());
        response.setGroupId(msg.getGroupId());
        response.setImageName(msg.getImageName());
        response.setImageType(msg.getImageType());
        response.setImageSize(msg.getImageSize());
        response.setTimestamp(msg.getTimestamp());
        response.setEncryptedData(msg.getEncryptedData());
        response.setStatus(msg.getStatus().name());
        response.setRetryCount(msg.getRetryCount());
        return response;
    }
}
