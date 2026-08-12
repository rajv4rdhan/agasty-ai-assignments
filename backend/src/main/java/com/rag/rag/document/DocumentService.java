package com.rag.rag.document;

import com.rag.rag.common.exception.InvalidFileException;
import com.rag.rag.common.exception.ResourceNotFoundException;
import com.rag.rag.common.tenant.TenantContext;
import com.rag.rag.conversation.MessageCitationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;

@Service
public class DocumentService {
    
    private static final Set<String> ALLOWED_TYPES = Set.of("pdf", "docx", "txt", "md", "markdown");
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20 MB
    
    private final DocumentRepository documentRepository;
    private final DocumentIngestionService ingestionService;
    private final ChunkRepository chunkRepository;
    private final MessageCitationRepository messageCitationRepository;
    
    public DocumentService(
            DocumentRepository documentRepository,
            DocumentIngestionService ingestionService,
            ChunkRepository chunkRepository,
            MessageCitationRepository messageCitationRepository) {
        this.documentRepository = documentRepository;
        this.ingestionService = ingestionService;
        this.chunkRepository = chunkRepository;
        this.messageCitationRepository = messageCitationRepository;
    }
    
    @Transactional
    public Document uploadDocument(MultipartFile file, String category) throws IOException {
        String tenantId = TenantContext.getTenantId();
        
        // Validate file
        validateFile(file);
        
        // Extract file info
        String fileName = file.getOriginalFilename();
        String fileType = extractFileType(fileName);
        long fileSize = file.getSize();
        byte[] content = file.getBytes();
        
        // Calculate content hash
        String contentHash = calculateSha256(content);
        
        // Check for duplicate
        var existing = documentRepository.findByTenantIdAndContentHash(tenantId, contentHash);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        // Create document entity
        String title = fileName.substring(0, fileName.lastIndexOf('.'));
        Document document = new Document(
            tenantId,
            title,
            fileName,
            fileType,
            fileSize,
            contentHash,
            category
        );
        
        document = documentRepository.save(document);
        
        // Store document ID and content for async processing
        Long documentId = document.getId();
        
        // Return document immediately - async processing will happen after transaction commits
        return document;
    }
    
    /**
     * Triggers async ingestion for a document. Should be called after the document
     * transaction has been committed.
     */
    public void triggerIngestion(Long documentId, byte[] content) {
        ingestionService.ingestDocument(documentId, content);
    }
    
    public List<Document> listDocuments(String category) {
        String tenantId = TenantContext.getTenantId();
        
        if (category != null && !category.isBlank()) {
            return documentRepository.findByTenantIdAndCategoryOrderByCreatedAtDesc(tenantId, category);
        }
        
        return documentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }
    
    public Document getDocument(Long documentId) {
        String tenantId = TenantContext.getTenantId();
        return documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
    }
    
    public int getChunkCount(Long documentId) {
        return chunkRepository.countByDocumentId(documentId);
    }
    
    /**
     * Deletes a document and all associated data (chunks, citations).
     * The deletion is cascading and transactional:
     * 1. Delete message citations referencing this document's chunks
     * 2. Delete all document chunks
     * 3. Delete the document record
     * 
     * @param documentId the document ID
     * @param tenantId the tenant ID
     * @throws ResourceNotFoundException if document not found or belongs to another tenant
     */
    @Transactional
    public void deleteDocument(Long documentId, String tenantId) {
        // Validate document exists and belongs to tenant
        Document document = documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
        
        // Delete message citations referencing this document (through document_id FK)
        // The database CASCADE will handle deleting citations when we delete the document
        // But we explicitly delete citations first for clarity and to avoid FK constraint issues
        messageCitationRepository.deleteByDocumentId(documentId);
        
        // Delete all chunks for this document
        chunkRepository.deleteByDocumentId(documentId);
        
        // Delete the document itself
        documentRepository.delete(document);
    }
    
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File is empty");
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidFileException("File size exceeds 20MB limit");
        }
        
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new InvalidFileException("Invalid file name");
        }
        
        String fileType = extractFileType(fileName);
        if (!ALLOWED_TYPES.contains(fileType.toLowerCase())) {
            throw new InvalidFileException("Unsupported file type. Allowed: PDF, DOCX, TXT, Markdown");
        }
    }
    
    private String extractFileType(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) {
            throw new InvalidFileException("File has no extension");
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }
    
    private String calculateSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
