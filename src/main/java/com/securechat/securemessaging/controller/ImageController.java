package com.securechat.securemessaging.controller;

import com.securechat.securemessaging.dto.ImageMessageResponse;
import com.securechat.securemessaging.service.ImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/images")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    // ── DM image endpoints ────────────────────────────────────

    /**
     * Upload and encrypt an image for a private conversation.
     * Multipart form: file=<image>, receiver=<username>
     */
    @PostMapping("/dm/send")
    public ResponseEntity<ImageMessageResponse> sendDmImage(
            @AuthenticationPrincipal String sender,
            @RequestParam("receiver") String receiver,
            @RequestParam("file") MultipartFile file) {

        return ResponseEntity.ok(imageService.sendDmImage(sender, receiver, file));
    }

    /**
     * Fetch all encrypted images in a DM conversation.
     */
    @GetMapping("/dm")
    public ResponseEntity<List<ImageMessageResponse>> getDmImages(
            @AuthenticationPrincipal String currentUser,
            @RequestParam("user2") String user2) {

        return ResponseEntity.ok(imageService.getDmImages(currentUser, user2));
    }

    /**
     * Fetch a single DM image by id (for lazy/on-demand loading).
     */
    @GetMapping("/dm/{imageId}")
    public ResponseEntity<ImageMessageResponse> getDmImage(
            @AuthenticationPrincipal String currentUser,
            @PathVariable Long imageId) {

        return ResponseEntity.ok(imageService.getDmImage(imageId, currentUser));
    }

    @PostMapping("/dm/ack")
    public ResponseEntity<Void> ackImage(@RequestParam("messageId") Long messageId) {
        imageService.markImageAsDelivered(messageId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/dm/read")
    public ResponseEntity<Void> readImages(
            @AuthenticationPrincipal String currentUser,
            @RequestBody Map<String, List<Long>> payload) {
        
        List<Long> messageIds = payload.get("messageIds");
        imageService.markImagesAsRead(currentUser, messageIds);
        return ResponseEntity.ok().build();
    }

    // ── Group image endpoints ─────────────────────────────────

    /**
     * Upload and encrypt an image for a group conversation.
     * Multipart form: file=<image>
     */
    @PostMapping("/group/{groupId}/send")
    public ResponseEntity<ImageMessageResponse> sendGroupImage(
            @AuthenticationPrincipal String sender,
            @PathVariable Long groupId,
            @RequestParam("file") MultipartFile file) {

        return ResponseEntity.ok(imageService.sendGroupImage(sender, groupId, file));
    }

    /**
     * Fetch all encrypted images in a group conversation.
     */
    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<ImageMessageResponse>> getGroupImages(
            @AuthenticationPrincipal String currentUser,
            @PathVariable Long groupId) {

        return ResponseEntity.ok(imageService.getGroupImages(groupId, currentUser));
    }

    /**
     * Fetch a single group image by id.
     */
    @GetMapping("/group/image/{imageId}")
    public ResponseEntity<ImageMessageResponse> getGroupImage(
            @AuthenticationPrincipal String currentUser,
            @PathVariable Long imageId) {

        return ResponseEntity.ok(imageService.getGroupImage(imageId, currentUser));
    }
}
