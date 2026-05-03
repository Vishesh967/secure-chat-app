package com.securechat.securemessaging.repository;

import com.securechat.securemessaging.model.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    List<GroupMember> findByGroupId(Long groupId);

    Optional<GroupMember> findByGroupIdAndUsername(Long groupId, String username);

    boolean existsByGroupIdAndUsername(Long groupId, String username);

    void deleteByGroupIdAndUsername(Long groupId, String username);
}
