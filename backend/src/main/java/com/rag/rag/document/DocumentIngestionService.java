package com.rag.rag.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentIngestionService {
    
    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
    
    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final DocumentParser documentParser;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    
    public DocumentIngestionService(
            DocumentRepository documentRepository,
            ChunkRepository chunkRepository,
            DocumentParser documentParser,
            ChunkingService chunkingService,
            EmbeddingService embeddingService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.documentParser = documentParser;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
    }
    
    @Async
    @Transactional
    public void ingestDocument(Long documentId, byte[] content) {
        log.info("Starting ingestion for document {}", documentId);
        
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
        
        try {
            // Parse document
            List<DocumentParser.PageContent> pages = documentParser.parse(content, document.getFileType());
            log.info("Parsed {} pages from document {}", pages.size(), documentId);
            
            // Clear content from memory immediately after parsing
            content = null;
            
            // Extract all text and create chunks first (before embeddings)
            List<ChunkingService.TextChunk> allTextChunks = new ArrayList<>();
            
            for (DocumentParser.PageContent page : pages) {
                if (page.text() == null || page.text().isBlank()) {
                    continue;
                }
                
                log.debug("Chunking page {} of document {}, text length: {}", 
                    page.pageNumber(), documentId, page.text().length());
                
                List<ChunkingService.TextChunk> pageChunks = chunkingService.chunkText(
                    page.text(), page.pageNumber()
                );
                
                allTextChunks.addAll(pageChunks);
            }
            
            // Clear pages from memory
            pages.clear();
            pages = null;
            
            if (allTextChunks.isEmpty()) {
                throw new RuntimeException("No content extracted from document");
            }
            
            log.info("Created {} text chunks from document {}", allTextChunks.size(), documentId);
            
            // Process chunks in VERY small batches: embed and save immediately to avoid memory accumulation
            int embeddingBatchSize = 3; // Very small batch size to minimize memory usage
            int chunkIndex = 0;
            
            for (int i = 0; i < allTextChunks.size(); i += embeddingBatchSize) {
                int end = Math.min(i + embeddingBatchSize, allTextChunks.size());
                List<ChunkingService.TextChunk> batchChunks = allTextChunks.subList(i, end);
                
                // Extract texts for this batch only
                List<String> batchTexts = batchChunks.stream()
                        .map(ChunkingService.TextChunk::text)
                        .toList();
                
                log.info("Generating embeddings for batch {}/{} ({} chunks)", 
                    (i / embeddingBatchSize) + 1, 
                    (allTextChunks.size() + embeddingBatchSize - 1) / embeddingBatchSize,
                    batchTexts.size());
                
                // Generate embeddings for this batch
                List<float[]> batchEmbeddings = embeddingService.embedBatch(batchTexts);
                
                // Immediately create and save DocumentChunk entities
                List<DocumentChunk> chunksToSave = new ArrayList<>();
                for (int j = 0; j < batchChunks.size(); j++) {
                    ChunkingService.TextChunk textChunk = batchChunks.get(j);
                    DocumentChunk chunk = new DocumentChunk(
                        document.getTenantId(),
                        document.getId(),
                        chunkIndex++,
                        textChunk.text(),
                        textChunk.pageNumber()
                    );
                    chunk.setEmbedding(batchEmbeddings.get(j));
                    chunksToSave.add(chunk);
                }
                
                // Save this batch immediately
                chunkRepository.saveAll(chunksToSave);
                
                log.info("Saved batch {}, total chunks saved: {}", 
                    (i / embeddingBatchSize) + 1, chunkIndex);
                
                // Clear batch data from memory explicitly
                batchTexts.clear();
                batchTexts = null;
                batchEmbeddings.clear();
                batchEmbeddings = null;
                chunksToSave.clear();
                chunksToSave = null;
            }
            
            // Clear all chunks from memory
            allTextChunks.clear();
            allTextChunks = null;
            
            log.info("Successfully saved {} chunks for document {}", chunkIndex, documentId);
            
            // Update document status
            document.setStatus(DocumentStatus.READY);
            documentRepository.save(document);
            
            log.info("Successfully ingested document {} with chunks", documentId);
            
        } catch (OutOfMemoryError e) {
            log.error("Out of memory while ingesting document {}: {}", documentId, e.getMessage());
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage("Document too large to process. Try a smaller file or increase memory allocation.");
            documentRepository.save(document);
        } catch (Exception e) {
            log.error("Failed to ingest document {}: {}", documentId, e.getMessage(), e);
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage(e.getMessage());
            documentRepository.save(document);
        }
    }
}
