package com.docquery.dto;

import com.docquery.model.Document;
import lombok.Getter;

import java.time.Instant;

@Getter
public class DocumentResponse {
    private final Long id;
    private final String fileName;
    private final int pageCount;
    private final int chunkCount;
    private final String status;
    private final Instant uploadedAt;

    public DocumentResponse(Document doc) {
        this.id = doc.getId();
        this.fileName = doc.getFileName();
        this.pageCount = doc.getPageCount();
        this.chunkCount = doc.getChunkCount();
        this.status = doc.getStatus().name();
        this.uploadedAt = doc.getUploadedAt();
    }
}
