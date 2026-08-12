package com.rag.rag.document;

import java.time.Instant;

public record DocumentDetailDTO(
        Long id,
        String title,
        String fileName,
        String fileType,
        Long fileSizeBytes,
        String status,
        String category,
        String errorMessage,
        Integer chunkCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static DocumentDetailDTO from(Document document, int chunkCount) {
        return new DocumentDetailDTO(
                document.getId(),
                document.getTitle(),
                document.getFileName(),
                document.getFileType(),
                document.getFileSizeBytes(),
                document.getStatus().name(),
                document.getCategory(),
                document.getErrorMessage(),
                chunkCount,
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
