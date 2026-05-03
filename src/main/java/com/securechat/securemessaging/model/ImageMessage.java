package com.securechat.securemessaging.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores an AES-CBC encrypted image for a private (DM) conversation.
 *
 * Storage layout (encryptedData column):
 *   Base64( IV[16 bytes] || AES-CBC ciphertext of the raw image bytes )
 *
 * The HMAC is computed over the Base64-encoded encryptedData string,
 * identical to how text messages are protected.
 */
@Entity
@Table(name = "image_messages")
public class ImageMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sender;

    @Column(nullable = false)
    private String receiver;

    /** Base64( IV || AES-CBC ciphertext ) of the raw image bytes. */
    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String encryptedData;

    /** HmacSHA256 over encryptedData for tamper detection. */
    @Column(nullable = false, length = 512)
    private String hmac;

    /** UUID nonce — prevents replay attacks. */
    @Column(nullable = false, unique = true)
    private String nonce;

    /** Original filename (e.g. "photo.jpg") — stored as-is, not a path. */
    @Column(nullable = false, length = 255)
    private String imageName;

    /** MIME type: image/jpeg, image/png, image/webp */
    @Column(nullable = false, length = 50)
    private String imageType;

    /** Original file size in bytes (before encryption). */
    @Column(nullable = false)
    private long imageSize;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    public ImageMessage() {
        this.timestamp = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }

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
