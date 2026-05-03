package com.securechat.securemessaging.repository;

import com.securechat.securemessaging.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Integer> {

    List<Message> findByReceiver(String receiver);

    List<Message> findBySenderAndReceiverOrderByTimestampAsc(String sender, String receiver);

    boolean existsByNonce(String nonce);

    /**
     * Returns the most recent message between two users (either direction).
     */
    @Query("SELECT m FROM Message m WHERE " +
           "(m.sender = :a AND m.receiver = :b) OR (m.sender = :b AND m.receiver = :a) " +
           "ORDER BY m.timestamp DESC LIMIT 1")
    Optional<Message> findLatestBetween(@Param("a") String a, @Param("b") String b);

    /**
     * Returns all distinct usernames that the given user has exchanged messages with.
     * Uses two separate queries and merges in Java to avoid CASE WHEN dialect issues.
     */
    @Query("SELECT DISTINCT m.receiver FROM Message m WHERE m.sender = :user")
    List<String> findReceiversForSender(@Param("user") String user);

    @Query("SELECT DISTINCT m.sender FROM Message m WHERE m.receiver = :user")
    List<String> findSendersForReceiver(@Param("user") String user);
}
