package com.securechat.securemessaging.repository;

import com.securechat.securemessaging.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {

    /**
     * Returns all groups that the given username belongs to (as member or admin).
     */
    @Query("SELECT g FROM Group g WHERE g.id IN " +
           "(SELECT gm.groupId FROM GroupMember gm WHERE gm.username = :username)")
    List<Group> findGroupsByMember(@Param("username") String username);
}
