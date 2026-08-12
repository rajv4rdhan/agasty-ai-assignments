package com.rag.rag.document;

import com.rag.rag.common.exception.InvalidFileException;
import com.rag.rag.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
    
    @Mock
    private DocumentRepository documentRepository;
    
    @Mock
    private DocumentIngestionService ingestionService;
    
    @Mock
    private ChunkRepository chunkRepository;
    
    @Mock
    private com.rag.rag.conversation.MessageCitationRepository citationRepository;
    
    @Mock
    private MultipartFile file;
    
    private DocumentService documentService;
    
    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentRepository, ingestionService, chunkRepository, citationRepository);
        TenantContext.setTenantId("test-tenant");
    }
    
    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }
    
    @Test
    void testUploadDocument_ValidPdf() throws IOException {
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("test.pdf");
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});
        when(documentRepository.findByTenantIdAndContentHash(any(), any())).thenReturn(Optional.empty());
        when(documentRepository.save(any())).thenAnswer(i -> {
            Document doc = i.getArgument(0);
            doc.setId(1L);
            return doc;
        });
        
        Document result = documentService.uploadDocument(file, "test-category");
        
        assertNotNull(result);
        assertEquals("test", result.getTitle());
        assertEquals("pdf", result.getFileType());
        verify(ingestionService, times(1)).ingestDocument(any(), any());
    }
    
    @Test
    void testUploadDocument_EmptyFile() {
        when(file.isEmpty()).thenReturn(true);
        
        assertThrows(InvalidFileException.class, () -> 
            documentService.uploadDocument(file, null)
        );
    }
    
    @Test
    void testUploadDocument_UnsupportedFileType() {
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("test.exe");
        
        assertThrows(InvalidFileException.class, () -> 
            documentService.uploadDocument(file, null)
        );
    }
    
    @Test
    void testUploadDocument_DuplicateFile() throws IOException {
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("test.pdf");
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});
        
        Document existingDoc = new Document();
        existingDoc.setId(1L);
        when(documentRepository.findByTenantIdAndContentHash(any(), any()))
            .thenReturn(Optional.of(existingDoc));
        
        Document result = documentService.uploadDocument(file, null);
        
        assertEquals(1L, result.getId());
        verify(ingestionService, never()).ingestDocument(any(), any());
    }
}
