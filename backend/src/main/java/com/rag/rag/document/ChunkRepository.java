package com.rag.rag.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChunkRepository extends JpaRepository<DocumentChunk, Long> {
    
    @Query(value = """
        SELECT dc.id, dc.tenant_id, dc.document_id, dc.chunk_index, dc.content, 
               dc.page_number, dc.embedding, dc.created_at,
               1 - (dc.embedding <=> CAST(:embedding AS vector)) AS similarity
        FROM document_chunks dc
        INNER JOIN documents d ON dc.document_id = d.id AND d.tenant_id = dc.tenant_id
        WHERE dc.tenant_id = :tenantId
          AND (1 - (dc.embedding <=> CAST(:embedding AS vector))) >= :threshold
        ORDER BY similarity DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findSimilarChunks(
        @Param("tenantId") String tenantId,
        @Param("embedding") String embedding,
        @Param("threshold") double threshold,
        @Param("limit") int limit
    );
    
    @Query(value = """
        SELECT dc.id, dc.tenant_id, dc.document_id, dc.chunk_index, dc.content, 
               dc.page_number, dc.embedding, dc.created_at,
               1 - (dc.embedding <=> CAST(:embedding AS vector)) AS similarity
        FROM document_chunks dc
        INNER JOIN documents d ON dc.document_id = d.id AND d.tenant_id = dc.tenant_id
        WHERE dc.tenant_id = :tenantId
          AND d.category = :category
          AND (1 - (dc.embedding <=> CAST(:embedding AS vector))) >= :threshold
        ORDER BY similarity DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findSimilarChunksByCategory(
        @Param("tenantId") String tenantId,
        @Param("category") String category,
        @Param("embedding") String embedding,
        @Param("threshold") double threshold,
        @Param("limit") int limit
    );
    
    void deleteByDocumentId(Long documentId);
    
    int countByDocumentId(Long documentId);
    
    @Query("SELECT COUNT(c) FROM DocumentChunk c WHERE c.documentId = :documentId")
    int countChunksByDocumentId(@Param("documentId") Long documentId);
    
    /**
     * Full-text search using PostgreSQL's tsvector and tsquery.
     * Returns chunks ranked by text relevance using ts_rank.
     */
    @Query(value = """
        SELECT dc.id, dc.tenant_id, dc.document_id, dc.chunk_index, dc.content, 
               dc.page_number, dc.created_at,
               ts_rank(to_tsvector('english', dc.content), plainto_tsquery('english', :query)) AS rank
        FROM document_chunks dc
        INNER JOIN documents d ON dc.document_id = d.id AND d.tenant_id = dc.tenant_id
        WHERE dc.tenant_id = :tenantId
          AND to_tsvector('english', dc.content) @@ plainto_tsquery('english', :query)
        ORDER BY rank DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findByFullTextSearch(
        @Param("tenantId") String tenantId,
        @Param("query") String query,
        @Param("limit") int limit
    );
    
    /**
     * Full-text search with category filter.
     */
    @Query(value = """
        SELECT dc.id, dc.tenant_id, dc.document_id, dc.chunk_index, dc.content, 
               dc.page_number, dc.created_at,
               ts_rank(to_tsvector('english', dc.content), plainto_tsquery('english', :query)) AS rank
        FROM document_chunks dc
        INNER JOIN documents d ON dc.document_id = d.id AND d.tenant_id = dc.tenant_id
        WHERE dc.tenant_id = :tenantId
          AND d.category = :category
          AND to_tsvector('english', dc.content) @@ plainto_tsquery('english', :query)
        ORDER BY rank DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findByFullTextSearchWithCategory(
        @Param("tenantId") String tenantId,
        @Param("category") String category,
        @Param("query") String query,
        @Param("limit") int limit
    );
}
