package com.securechat.securemessaging.dto;

import java.time.LocalDateTime;

/**
 * Returned to the client after sending or fetching an image message.
 *
 * The decryptedDataUrl field carries a data: URI (data:image/jpeg;base64,...)
 * that the browser can render directly as an <img> src.
 * It is populated only when the image is successfully decrypted server-side.
 */
public class ImageMessageResponse {

    private Long   id;
    private String sender;
    private String receiver;       // null for group images
    private Long   groupId;        // null for DM images
    private String imageName;
    private String imageType;
    private long   imageSize;
    private LocalDateTime timestamp;

    /** data:<imageType>;base64,<raw bytes as base64> — ready for <img src="..."> */
    private String decryptedDataUrl;

    /** "IMAGE" — lets the frontend distinguish from text messages in a unified list */
    private final String messageType = "IMAGE";

    public ImageMessageResponse() {}

    // ── Getters ───────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public String getImageName() { return imageName; }
    public void setImageName(String imageName) { this.imageName = imageName; }

    public String getImageType() { return imageType; }
    public void setImageType(String imageType) { this.imageType = imageType; }

    public long getImageSize() { return imageSize; }
    public void setImageSize(long imageSize) { this.imageSize = imageSize; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getDecryptedDataUrl() { return decryptedDataUrl; }
    public void setDecryptedDataUrl(String decryptedDataUrl) { this.decryptedDataUrl = decryptedDataUrl; }

    public String getMessageType() { return messageType; }
}
