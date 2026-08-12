package com.rag.rag.document;

import java.time.Instant;

public record DocumentListDTO(
        Long id,
        String title,
        String fileName,
        String fileType,
        Long fileSizeBytes,
        String status,
        String category,
        Integer chunkCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static DocumentListDTO from(Document document, int chunkCount) {
        return new DocumentListDTO(
                document.getId(),
                document.getTitle(),
                document.getFileName(),
                document.getFileType(),
                document.getFileSizeBytes(),
                document.getStatus().name(),
                document.getCategory(),
                chunkCount,
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
