package com.docquery.controller;

import com.docquery.dto.DocumentResponse;
import com.docquery.model.Document;
import com.docquery.model.User;
import com.docquery.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> upload(@AuthenticationPrincipal User user,
                                                     @RequestParam("file") MultipartFile file) throws IOException {
        Document document = documentService.uploadAndProcess(user, file);
        return ResponseEntity.ok(new DocumentResponse(document));
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> list(@AuthenticationPrincipal User user) {
        List<DocumentResponse> documents = documentService.listForUser(user).stream()
                .map(DocumentResponse::new)
                .toList();
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> get(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(new DocumentResponse(documentService.getOwned(id, user)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        documentService.deleteDocument(id, user);
        return ResponseEntity.noContent().build();
    }
}
