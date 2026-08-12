package com.rag.rag.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MessageCitationRepository extends JpaRepository<MessageCitation, Long> {
    
    List<MessageCitation> findByMessageIdOrderBySimilarityScoreDesc(Long messageId);
    
    @Modifying
    @Query("DELETE FROM MessageCitation mc WHERE mc.documentId = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);
}
