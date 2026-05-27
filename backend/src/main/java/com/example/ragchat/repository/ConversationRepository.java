package com.example.ragchat.repository;

import com.example.ragchat.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {
    
    Page<Conversation> findByUserIdAndIsArchivedFalse(String userId, Pageable pageable);
    
    Optional<Conversation> findByIdAndUserId(String id, String userId);
    
    List<Conversation> findByUserId(String userId);
}
