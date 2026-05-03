package com.securechat.securemessaging.dto;

import java.time.LocalDateTime;

/**
 * Represents one row in the sidebar conversation list —
 * works for both DM and group conversations.
 */
public class ConversationPreview {

    public enum Type { DM, GROUP }

    private Type type;

    /** For DM: the other user's username. For GROUP: the group name. */
    private String name;

    /** For GROUP: the group id. Null for DM. */
    private Long groupId;

    private String lastMessage;
    private LocalDateTime lastMessageTime;

    public ConversationPreview() {}

    // ── DM factory ────────────────────────────────────────────
    public static ConversationPreview dm(String username,
                                         String lastMessage,
                                         LocalDateTime lastMessageTime) {
        ConversationPreview p = new ConversationPreview();
        p.type            = Type.DM;
        p.name            = username;
        p.lastMessage     = lastMessage;
        p.lastMessageTime = lastMessageTime;
        return p;
    }

    // ── Group factory ─────────────────────────────────────────
    public static ConversationPreview group(Long groupId,
                                             String groupName,
                                             String lastMessage,
                                             LocalDateTime lastMessageTime) {
        ConversationPreview p = new ConversationPreview();
        p.type            = Type.GROUP;
        p.groupId         = groupId;
        p.name            = groupName;
        p.lastMessage     = lastMessage;
        p.lastMessageTime = lastMessageTime;
        return p;
    }

    public Type getType() { return type; }
    public String getName() { return name; }
    public Long getGroupId() { return groupId; }
    public String getLastMessage() { return lastMessage; }
    public LocalDateTime getLastMessageTime() { return lastMessageTime; }
}
