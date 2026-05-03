package com.securechat.securemessaging.service;

import com.securechat.securemessaging.dto.GroupMessageResponse;
import com.securechat.securemessaging.dto.GroupResponse;
import com.securechat.securemessaging.model.Group;
import com.securechat.securemessaging.model.GroupMember;
import com.securechat.securemessaging.model.GroupMessage;
import com.securechat.securemessaging.repository.GroupMemberRepository;
import com.securechat.securemessaging.repository.GroupMessageRepository;
import com.securechat.securemessaging.repository.GroupRepository;
import com.securechat.securemessaging.security.AESUtil;
import com.securechat.securemessaging.security.HMACUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);
    private static final String HMAC_KEY = "1234567890123456";

    private final GroupRepository          groupRepo;
    private final GroupMemberRepository    memberRepo;
    private final GroupMessageRepository   messageRepo;

    public GroupService(GroupRepository groupRepo,
                        GroupMemberRepository memberRepo,
                        GroupMessageRepository messageRepo) {
        this.groupRepo   = groupRepo;
        this.memberRepo  = memberRepo;
        this.messageRepo = messageRepo;
    }

    // ── Create group ──────────────────────────────────────────

    @Transactional
    public GroupResponse createGroup(String admin, String name, List<String> initialMembers) {
        Group group = new Group();
        group.setName(name.trim());
        group.setAdmin(admin);
        group = groupRepo.save(group);

        // Admin is always a member
        memberRepo.save(new GroupMember(group.getId(), admin));

        if (initialMembers != null) {
            for (String member : initialMembers) {
                if (!member.isBlank() && !member.equals(admin)) {
                    if (!memberRepo.existsByGroupIdAndUsername(group.getId(), member)) {
                        memberRepo.save(new GroupMember(group.getId(), member));
                    }
                }
            }
        }

        return toGroupResponse(group);
    }

    // ── Add member ────────────────────────────────────────────

    @Transactional
    public GroupResponse addMember(Long groupId, String requestingUser, String newMember) {
        Group group = getGroupOrThrow(groupId);
        assertAdmin(group, requestingUser);

        if (memberRepo.existsByGroupIdAndUsername(groupId, newMember)) {
            throw new RuntimeException(newMember + " is already a member");
        }

        memberRepo.save(new GroupMember(groupId, newMember));
        return toGroupResponse(group);
    }

    // ── Remove member ─────────────────────────────────────────

    @Transactional
    public GroupResponse removeMember(Long groupId, String requestingUser, String targetMember) {
        Group group = getGroupOrThrow(groupId);
        assertAdmin(group, requestingUser);

        if (targetMember.equals(group.getAdmin())) {
            throw new RuntimeException("Cannot remove the group admin");
        }

        memberRepo.deleteByGroupIdAndUsername(groupId, targetMember);
        return toGroupResponse(group);
    }

    // ── Leave group ───────────────────────────────────────────

    @Transactional
    public void leaveGroup(Long groupId, String username) {
        Group group = getGroupOrThrow(groupId);
        if (username.equals(group.getAdmin())) {
            throw new RuntimeException("Admin cannot leave — transfer ownership or delete the group");
        }
        memberRepo.deleteByGroupIdAndUsername(groupId, username);
    }

    // ── Send group message ────────────────────────────────────

    @Transactional
    public GroupMessageResponse sendMessage(Long groupId, String sender, String content) {
        Group group = getGroupOrThrow(groupId);

        if (!memberRepo.existsByGroupIdAndUsername(groupId, sender)) {
            throw new RuntimeException("You are not a member of this group");
        }

        GroupMessage msg = new GroupMessage();
        msg.setGroupId(groupId);
        msg.setSender(sender);

        try {
            String encrypted = AESUtil.encrypt(content);
            msg.setContent(encrypted);

            String nonce;
            do { nonce = UUID.randomUUID().toString(); }
            while (messageRepo.existsByNonce(nonce));
            msg.setNonce(nonce);

            msg.setHmac(HMACUtil.generateHMAC(encrypted, HMAC_KEY));
        } catch (Exception e) {
            log.error("Group message encryption failed group={} sender={}", groupId, sender, e);
            throw new RuntimeException("Failed to send message — encryption error");
        }

        GroupMessage saved = messageRepo.save(msg);
        return toMessageResponse(saved, content);
    }

    // ── Get group messages ────────────────────────────────────

    public List<GroupMessageResponse> getMessages(Long groupId, String requestingUser) {
        getGroupOrThrow(groupId);

        if (!memberRepo.existsByGroupIdAndUsername(groupId, requestingUser)) {
            throw new RuntimeException("You are not a member of this group");
        }

        return messageRepo.findByGroupIdOrderByTimestampAsc(groupId)
                .stream()
                .map(this::decryptAndMap)
                .collect(Collectors.toList());
    }

    // ── List user groups ──────────────────────────────────────

    public List<GroupResponse> getUserGroups(String username) {
        return groupRepo.findGroupsByMember(username)
                .stream()
                .map(this::toGroupResponse)
                .collect(Collectors.toList());
    }

    // ── Get single group ──────────────────────────────────────

    public GroupResponse getGroup(Long groupId, String requestingUser) {
        Group group = getGroupOrThrow(groupId);
        if (!memberRepo.existsByGroupIdAndUsername(groupId, requestingUser)) {
            throw new RuntimeException("You are not a member of this group");
        }
        return toGroupResponse(group);
    }

    // ── Private helpers ───────────────────────────────────────

    private Group getGroupOrThrow(Long groupId) {
        return groupRepo.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
    }

    private void assertAdmin(Group group, String user) {
        if (!group.getAdmin().equals(user)) {
            throw new RuntimeException("Only the group admin can perform this action");
        }
    }

    private GroupResponse toGroupResponse(Group group) {
        GroupResponse r = new GroupResponse();
        r.setId(group.getId());
        r.setName(group.getName());
        r.setAdmin(group.getAdmin());
        r.setCreatedAt(group.getCreatedAt());

        List<String> members = memberRepo.findByGroupId(group.getId())
                .stream()
                .map(GroupMember::getUsername)
                .collect(Collectors.toList());
        r.setMembers(members);

        // Last message preview
        messageRepo.findTopByGroupIdOrderByTimestampDesc(group.getId())
                .ifPresent(last -> {
                    r.setLastMessageTime(last.getTimestamp());
                    try {
                        boolean valid = HMACUtil.verifyHMAC(last.getContent(), HMAC_KEY, last.getHmac());
                        if (valid) {
                            String decrypted = AESUtil.decrypt(last.getContent());
                            r.setLastMessage(truncate(decrypted, 60));
                        }
                    } catch (Exception ignored) {}
                });

        return r;
    }

    private GroupMessageResponse decryptAndMap(GroupMessage msg) {
        try {
            boolean valid = HMACUtil.verifyHMAC(msg.getContent(), HMAC_KEY, msg.getHmac());
            if (!valid) {
                log.warn("HMAC failed for group message id={}", msg.getId());
                return toMessageResponse(msg, "[integrity check failed]");
            }
            return toMessageResponse(msg, AESUtil.decrypt(msg.getContent()));
        } catch (Exception e) {
            log.error("Decryption failed for group message id={}", msg.getId(), e);
            return toMessageResponse(msg, "[decryption error]");
        }
    }

    private GroupMessageResponse toMessageResponse(GroupMessage msg, String content) {
        return new GroupMessageResponse(
                msg.getId(), msg.getGroupId(), msg.getSender(),
                content, msg.getTimestamp());
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
