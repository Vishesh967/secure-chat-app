package com.securechat.securemessaging.service;

import com.securechat.securemessaging.dto.GroupMessageResponse;
import com.securechat.securemessaging.dto.GroupResponse;
import com.securechat.securemessaging.model.Group;
import com.securechat.securemessaging.model.GroupMember;
import com.securechat.securemessaging.model.GroupMessage;
import com.securechat.securemessaging.repository.GroupMemberRepository;
import com.securechat.securemessaging.repository.GroupMessageRepository;
import com.securechat.securemessaging.repository.GroupRepository;
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

    private final GroupRepository groupRepo;
    private final GroupMemberRepository memberRepo;
    private final GroupMessageRepository messageRepo;

    public GroupService(GroupRepository groupRepo,
                        GroupMemberRepository memberRepo,
                        GroupMessageRepository messageRepo) {
        this.groupRepo = groupRepo;
        this.memberRepo = memberRepo;
        this.messageRepo = messageRepo;
    }

    @Transactional
    public GroupResponse createGroup(String admin, String name, List<String> initialMembers) {
        Group group = new Group();
        group.setName(name.trim());
        group.setAdmin(admin);
        group = groupRepo.save(group);

        memberRepo.save(new GroupMember(group.getId(), admin));

        if (initialMembers != null) {
            for (String member : initialMembers) {
                if (!member.isBlank() && !member.equals(admin)
                        && !memberRepo.existsByGroupIdAndUsername(group.getId(), member)) {
                    memberRepo.save(new GroupMember(group.getId(), member));
                }
            }
        }

        return toGroupResponse(group);
    }

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

    @Transactional
    public void leaveGroup(Long groupId, String username) {
        Group group = getGroupOrThrow(groupId);
        if (username.equals(group.getAdmin())) {
            throw new RuntimeException("Admin cannot leave - transfer ownership or delete the group");
        }
        memberRepo.deleteByGroupIdAndUsername(groupId, username);
    }

    @Transactional
    public GroupMessageResponse sendMessage(Long groupId,
                                            String sender,
                                            String encryptedContent,
                                            String requestedNonce) {
        getGroupOrThrow(groupId);

        if (!memberRepo.existsByGroupIdAndUsername(groupId, sender)) {
            throw new RuntimeException("You are not a member of this group");
        }
        if (encryptedContent == null || encryptedContent.isBlank()) {
            throw new RuntimeException("Encrypted message content is required");
        }

        GroupMessage msg = new GroupMessage();
        msg.setGroupId(groupId);
        msg.setSender(sender);
        msg.setContent(encryptedContent);
        msg.setNonce(resolveNonce(requestedNonce));

        try {
            msg.setHmac(HMACUtil.generateHMAC(encryptedContent, HMAC_KEY));
        } catch (Exception e) {
            log.error("Group message persistence failed group={} sender={}", groupId, sender, e);
            throw new RuntimeException("Failed to store encrypted group message");
        }

        return toMessageResponse(messageRepo.save(msg));
    }

    public List<GroupMessageResponse> getMessages(Long groupId, String requestingUser) {
        getGroupOrThrow(groupId);

        if (!memberRepo.existsByGroupIdAndUsername(groupId, requestingUser)) {
            throw new RuntimeException("You are not a member of this group");
        }

        return messageRepo.findByGroupIdOrderByTimestampAsc(groupId)
                .stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList());
    }

    public List<GroupResponse> getUserGroups(String username) {
        return groupRepo.findGroupsByMember(username)
                .stream()
                .map(this::toGroupResponse)
                .collect(Collectors.toList());
    }

    public GroupResponse getGroup(Long groupId, String requestingUser) {
        Group group = getGroupOrThrow(groupId);
        if (!memberRepo.existsByGroupIdAndUsername(groupId, requestingUser)) {
            throw new RuntimeException("You are not a member of this group");
        }
        return toGroupResponse(group);
    }

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
        GroupResponse response = new GroupResponse();
        response.setId(group.getId());
        response.setName(group.getName());
        response.setAdmin(group.getAdmin());
        response.setCreatedAt(group.getCreatedAt());

        List<String> members = memberRepo.findByGroupId(group.getId())
                .stream()
                .map(GroupMember::getUsername)
                .collect(Collectors.toList());
        response.setMembers(members);

        messageRepo.findTopByGroupIdOrderByTimestampDesc(group.getId())
                .ifPresent(last -> {
                    response.setLastMessageTime(last.getTimestamp());
                    response.setLastMessage("Encrypted message");
                });

        return response;
    }

    private String resolveNonce(String requestedNonce) {
        if (requestedNonce != null && !requestedNonce.isBlank()) {
            if (messageRepo.existsByNonce(requestedNonce)) {
                throw new RuntimeException("Duplicate message nonce");
            }
            return requestedNonce;
        }

        String nonce;
        do {
            nonce = UUID.randomUUID().toString();
        } while (messageRepo.existsByNonce(nonce));
        return nonce;
    }

    private GroupMessageResponse toMessageResponse(GroupMessage msg) {
        return new GroupMessageResponse(
                msg.getId(),
                msg.getGroupId(),
                msg.getSender(),
                msg.getContent(),
                msg.getNonce(),
                msg.getTimestamp());
    }
}
