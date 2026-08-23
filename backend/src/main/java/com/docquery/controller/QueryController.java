package com.docquery.controller;

import com.docquery.dto.AskRequest;
import com.docquery.dto.AskResponse;
import com.docquery.model.Document;
import com.docquery.model.User;
import com.docquery.service.DocumentService;
import com.docquery.service.QueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents/{documentId}")
public class QueryController {

    private final QueryService queryService;
    private final DocumentService documentService;

    public QueryController(QueryService queryService, DocumentService documentService) {
        this.queryService = queryService;
        this.documentService = documentService;
    }

    @PostMapping("/ask")
    public ResponseEntity<AskResponse> ask(@AuthenticationPrincipal User user,
                                            @PathVariable Long documentId,
                                            @Valid @RequestBody AskRequest request) {
        Document document = documentService.getOwned(documentId, user);
        AskResponse response = queryService.ask(user, document, request.getQuestion());
        return ResponseEntity.ok(response);
    }
}
