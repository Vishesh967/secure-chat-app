package com.securechat.securemessaging.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores an AES-CBC encrypted image sent inside a group conversation.
 * Same encryption/HMAC scheme as ImageMessage.
 */
@Entity
@Table(name = "group_image_messages")
public class GroupImageMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(nullable = false)
    private String sender;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String encryptedData;

    @Column(nullable = false, length = 512)
    private String hmac;

    @Column(nullable = false, unique = true)
    private String nonce;

    @Column(nullable = false, length = 255)
    private String imageName;

    @Column(nullable = false, length = 50)
    private String imageType;

    @Column(nullable = false)
    private long imageSize;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    public GroupImageMessage() {
        this.timestamp = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getEncryptedData() { return encryptedData; }
    public void setEncryptedData(String encryptedData) { this.encryptedData = encryptedData; }

    public String getHmac() { return hmac; }
    public void setHmac(String hmac) { this.hmac = hmac; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public String getImageName() { return imageName; }
    public void setImageName(String imageName) { this.imageName = imageName; }

    public String getImageType() { return imageType; }
    public void setImageType(String imageType) { this.imageType = imageType; }

    public long getImageSize() { return imageSize; }
    public void setImageSize(long imageSize) { this.imageSize = imageSize; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
