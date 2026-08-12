package com.rag.rag.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    
    List<Document> findByTenantIdOrderByCreatedAtDesc(String tenantId);
    
    List<Document> findByTenantIdAndCategoryOrderByCreatedAtDesc(String tenantId, String category);
    
    Optional<Document> findByTenantIdAndContentHash(String tenantId, String contentHash);
    
    Optional<Document> findByIdAndTenantId(Long id, String tenantId);
}
