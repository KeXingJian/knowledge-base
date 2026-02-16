package com.kxj.knowledgebase.repository;

import com.kxj.knowledgebase.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findBySessionId(String sessionId);

    List<Conversation> findAllByOrderByCreateTimeDesc();

    void deleteBySessionId(String sessionId);
}
