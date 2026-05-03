package com.securechat.securemessaging.service;

import com.securechat.securemessaging.dto.ImageMessageResponse;
import com.securechat.securemessaging.model.GroupImageMessage;
import com.securechat.securemessaging.model.ImageMessage;
import com.securechat.securemessaging.repository.GroupImageMessageRepository;
import com.securechat.securemessaging.repository.GroupMemberRepository;
import com.securechat.securemessaging.repository.ImageMessageRepository;
import com.securechat.securemessaging.security.AESUtil;
import com.securechat.securemessaging.security.HMACUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);

    /** Shared key — same as text messaging for consistency. */
    private static final String HMAC_KEY = "1234567890123456";

    /** 10 MB upload limit. */
    private static final long MAX_BYTES = 10 * 1024 * 1024;

    /** Allowed MIME types. */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    private final ImageMessageRepository      imageRepo;
    private final GroupImageMessageRepository groupImageRepo;
    private final GroupMemberRepository       memberRepo;

    public ImageService(ImageMessageRepository imageRepo,
                        GroupImageMessageRepository groupImageRepo,
                        GroupMemberRepository memberRepo) {
        this.imageRepo      = imageRepo;
        this.groupImageRepo = groupImageRepo;
        this.memberRepo     = memberRepo;
    }

    // ── Send DM image ─────────────────────────────────────────

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
            byte[] rawBytes = file.getBytes();

            // Encrypt raw image bytes → Base64(IV || ciphertext)
            String encrypted = AESUtil.encryptBytes(rawBytes);
            msg.setEncryptedData(encrypted);

            // Nonce for replay protection
            String nonce;
            do { nonce = UUID.randomUUID().toString(); }
            while (imageRepo.existsByNonce(nonce));
            msg.setNonce(nonce);

            // HMAC over the encrypted string
            msg.setHmac(HMACUtil.generateHMAC(encrypted, HMAC_KEY));

        } catch (Exception e) {
            log.error("DM image encryption failed sender={} receiver={}", sender, receiver, e);
            throw new RuntimeException("Image encryption failed");
        }

        ImageMessage saved = imageRepo.save(msg);
        return toResponse(saved);
    }

    // ── Send group image ──────────────────────────────────────

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
            byte[] rawBytes = file.getBytes();

            String encrypted = AESUtil.encryptBytes(rawBytes);
            msg.setEncryptedData(encrypted);

            String nonce;
            do { nonce = UUID.randomUUID().toString(); }
            while (groupImageRepo.existsByNonce(nonce));
            msg.setNonce(nonce);

            msg.setHmac(HMACUtil.generateHMAC(encrypted, HMAC_KEY));

        } catch (Exception e) {
            log.error("Group image encryption failed group={} sender={}", groupId, sender, e);
            throw new RuntimeException("Image encryption failed");
        }

        GroupImageMessage saved = groupImageRepo.save(msg);
        return toGroupResponse(saved);
    }

    // ── Fetch DM images ───────────────────────────────────────

    public List<ImageMessageResponse> getDmImages(String user1, String user2) {
        return imageRepo.findConversationImages(user1, user2)
                .stream()
                .map(this::decryptAndMapDm)
                .collect(Collectors.toList());
    }

    // ── Fetch group images ────────────────────────────────────

    public List<ImageMessageResponse> getGroupImages(Long groupId, String requestingUser) {
        if (!memberRepo.existsByGroupIdAndUsername(groupId, requestingUser)) {
            throw new RuntimeException("You are not a member of this group");
        }
        return groupImageRepo.findByGroupIdOrderByTimestampAsc(groupId)
                .stream()
                .map(this::decryptAndMapGroup)
                .collect(Collectors.toList());
    }

    // ── Fetch single DM image (for lazy loading) ──────────────

    public ImageMessageResponse getDmImage(Long imageId, String requestingUser) {
        ImageMessage msg = imageRepo.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Image not found"));

        if (!msg.getSender().equals(requestingUser) && !msg.getReceiver().equals(requestingUser)) {
            throw new RuntimeException("Access denied");
        }

        return decryptAndMapDm(msg);
    }

    // ── Fetch single group image ──────────────────────────────

    public ImageMessageResponse getGroupImage(Long imageId, String requestingUser) {
        GroupImageMessage msg = groupImageRepo.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Image not found"));

        if (!memberRepo.existsByGroupIdAndUsername(msg.getGroupId(), requestingUser)) {
            throw new RuntimeException("Access denied");
        }

        return decryptAndMapGroup(msg);
    }

    // ── Private helpers ───────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("No file provided");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new RuntimeException("File too large — maximum 10 MB");
        }
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_TYPES.contains(ct.toLowerCase())) {
            throw new RuntimeException("Unsupported file type — use JPG, PNG, or WEBP");
        }
    }

    private String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) return "image";
        // Keep only safe characters
        return original.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private ImageMessageResponse decryptAndMapDm(ImageMessage msg) {
        ImageMessageResponse r = toResponse(msg);
        try {
            boolean valid = HMACUtil.verifyHMAC(msg.getEncryptedData(), HMAC_KEY, msg.getHmac());
            if (!valid) {
                log.warn("HMAC failed for DM image id={}", msg.getId());
                r.setDecryptedDataUrl(null);
                return r;
            }
            byte[] raw = AESUtil.decryptBytes(msg.getEncryptedData());
            r.setDecryptedDataUrl(buildDataUrl(msg.getImageType(), raw));
        } catch (Exception e) {
            log.error("Decryption failed for DM image id={}", msg.getId(), e);
            r.setDecryptedDataUrl(null);
        }
        return r;
    }

    private ImageMessageResponse decryptAndMapGroup(GroupImageMessage msg) {
        ImageMessageResponse r = toGroupResponse(msg);
        try {
            boolean valid = HMACUtil.verifyHMAC(msg.getEncryptedData(), HMAC_KEY, msg.getHmac());
            if (!valid) {
                log.warn("HMAC failed for group image id={}", msg.getId());
                r.setDecryptedDataUrl(null);
                return r;
            }
            byte[] raw = AESUtil.decryptBytes(msg.getEncryptedData());
            r.setDecryptedDataUrl(buildDataUrl(msg.getImageType(), raw));
        } catch (Exception e) {
            log.error("Decryption failed for group image id={}", msg.getId(), e);
            r.setDecryptedDataUrl(null);
        }
        return r;
    }

    private String buildDataUrl(String mimeType, byte[] raw) {
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(raw);
    }

    private ImageMessageResponse toResponse(ImageMessage msg) {
        ImageMessageResponse r = new ImageMessageResponse();
        r.setId(msg.getId());
        r.setSender(msg.getSender());
        r.setReceiver(msg.getReceiver());
        r.setImageName(msg.getImageName());
        r.setImageType(msg.getImageType());
        r.setImageSize(msg.getImageSize());
        r.setTimestamp(msg.getTimestamp());
        return r;
    }

    private ImageMessageResponse toGroupResponse(GroupImageMessage msg) {
        ImageMessageResponse r = new ImageMessageResponse();
        r.setId(msg.getId());
        r.setSender(msg.getSender());
        r.setGroupId(msg.getGroupId());
        r.setImageName(msg.getImageName());
        r.setImageType(msg.getImageType());
        r.setImageSize(msg.getImageSize());
        r.setTimestamp(msg.getTimestamp());
        return r;
    }
}
