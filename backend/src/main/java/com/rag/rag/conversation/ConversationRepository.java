package com.rag.rag.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    
    List<Conversation> findByTenantIdOrderByUpdatedAtDesc(String tenantId);
    
    Optional<Conversation> findByIdAndTenantId(Long id, String tenantId);
}
