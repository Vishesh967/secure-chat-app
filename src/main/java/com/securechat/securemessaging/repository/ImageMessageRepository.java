package com.securechat.securemessaging.repository;

import com.securechat.securemessaging.model.ImageMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ImageMessageRepository extends JpaRepository<ImageMessage, Long> {

    boolean existsByNonce(String nonce);

    /**
     * All image messages between two users, sorted oldest-first.
     */
    @Query("SELECT m FROM ImageMessage m WHERE " +
           "(m.sender = :a AND m.receiver = :b) OR (m.sender = :b AND m.receiver = :a) " +
           "ORDER BY m.timestamp ASC")
    List<ImageMessage> findConversationImages(@Param("a") String a, @Param("b") String b);
}
