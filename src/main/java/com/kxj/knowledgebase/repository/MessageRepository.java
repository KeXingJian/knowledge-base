package com.kxj.knowledgebase.repository;

import com.kxj.knowledgebase.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreateTimeAsc(Long conversationId);

    void deleteByConversationId(Long conversationId);

    Integer countByConversationId(Long conversationId);
}
