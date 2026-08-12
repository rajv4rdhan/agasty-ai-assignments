package com.rag.rag.document;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    
    private final DocumentService documentService;
    
    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }
    
    @PostMapping("/upload")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", required = false) String category) throws IOException {
        
        // Store file content before creating document
        byte[] content = file.getBytes();
        
        // Create document (transactional)
        Document document = documentService.uploadDocument(file, category);
        
        // Trigger async ingestion after transaction commits
        documentService.triggerIngestion(document.getId(), content);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(DocumentResponse.from(document));
    }
    
    @GetMapping
    public ResponseEntity<List<DocumentResponse>> listDocuments(
            @RequestParam(value = "category", required = false) String category) {
        
        List<Document> documents = documentService.listDocuments(category);
        List<DocumentResponse> response = documents.stream()
                .map(DocumentResponse::from)
                .toList();
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable Long id) {
        Document document = documentService.getDocument(id);
        return ResponseEntity.ok(DocumentResponse.from(document));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        documentService.deleteDocument(id, tenantId);
        return ResponseEntity.noContent().build();
    }
    
    public record DocumentResponse(
            Long id,
            String title,
            String fileName,
            String fileType,
            Long fileSizeBytes,
            String status,
            String category,
            String errorMessage,
            String createdAt,
            String updatedAt
    ) {
        public static DocumentResponse from(Document document) {
            return new DocumentResponse(
                    document.getId(),
                    document.getTitle(),
                    document.getFileName(),
                    document.getFileType(),
                    document.getFileSizeBytes(),
                    document.getStatus().name(),
                    document.getCategory(),
                    document.getErrorMessage(),
                    document.getCreatedAt().toString(),
                    document.getUpdatedAt().toString()
            );
        }
    }
}
