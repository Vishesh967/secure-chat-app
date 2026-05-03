package com.securechat.securemessaging.repository;

import com.securechat.securemessaging.model.GroupImageMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupImageMessageRepository extends JpaRepository<GroupImageMessage, Long> {

    boolean existsByNonce(String nonce);

    List<GroupImageMessage> findByGroupIdOrderByTimestampAsc(Long groupId);
}
