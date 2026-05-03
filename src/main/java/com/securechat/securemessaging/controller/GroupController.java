package com.securechat.securemessaging.controller;

import com.securechat.securemessaging.dto.CreateGroupRequest;
import com.securechat.securemessaging.dto.GroupMessageRequest;
import com.securechat.securemessaging.dto.GroupMessageResponse;
import com.securechat.securemessaging.dto.GroupResponse;
import com.securechat.securemessaging.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    /** Create a new group. The creator automatically becomes admin and first member. */
    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(
            @AuthenticationPrincipal String admin,
            @Valid @RequestBody CreateGroupRequest request) {

        return ResponseEntity.ok(
                groupService.createGroup(admin, request.getName(), request.getMembers()));
    }

    /** List all groups the authenticated user belongs to. */
    @GetMapping
    public ResponseEntity<List<GroupResponse>> listMyGroups(
            @AuthenticationPrincipal String username) {

        return ResponseEntity.ok(groupService.getUserGroups(username));
    }

    /** Get a single group's details (members, last message, etc.). */
    @GetMapping("/{groupId}")
    public ResponseEntity<GroupResponse> getGroup(
            @AuthenticationPrincipal String username,
            @PathVariable Long groupId) {

        return ResponseEntity.ok(groupService.getGroup(groupId, username));
    }

    /** Add a member (admin only). Body: { "username": "alice" } */
    @PostMapping("/{groupId}/members")
    public ResponseEntity<GroupResponse> addMember(
            @AuthenticationPrincipal String admin,
            @PathVariable Long groupId,
            @RequestBody Map<String, String> body) {

        String newMember = body.get("username");
        if (newMember == null || newMember.isBlank()) {
            throw new RuntimeException("username is required");
        }
        return ResponseEntity.ok(groupService.addMember(groupId, admin, newMember.trim()));
    }

    /** Remove a member (admin only). */
    @DeleteMapping("/{groupId}/members/{username}")
    public ResponseEntity<GroupResponse> removeMember(
            @AuthenticationPrincipal String admin,
            @PathVariable Long groupId,
            @PathVariable String username) {

        return ResponseEntity.ok(groupService.removeMember(groupId, admin, username));
    }

    /** Leave a group (non-admin members only). */
    @DeleteMapping("/{groupId}/leave")
    public ResponseEntity<Void> leaveGroup(
            @AuthenticationPrincipal String username,
            @PathVariable Long groupId) {

        groupService.leaveGroup(groupId, username);
        return ResponseEntity.noContent().build();
    }

    /** Send an encrypted message to a group. */
    @PostMapping("/{groupId}/messages")
    public ResponseEntity<GroupMessageResponse> sendMessage(
            @AuthenticationPrincipal String sender,
            @PathVariable Long groupId,
            @Valid @RequestBody GroupMessageRequest request) {

        return ResponseEntity.ok(
                groupService.sendMessage(groupId, sender, request.getContent()));
    }

    /** Fetch all messages for a group (decrypted). */
    @GetMapping("/{groupId}/messages")
    public ResponseEntity<List<GroupMessageResponse>> getMessages(
            @AuthenticationPrincipal String username,
            @PathVariable Long groupId) {

        return ResponseEntity.ok(groupService.getMessages(groupId, username));
    }
}
