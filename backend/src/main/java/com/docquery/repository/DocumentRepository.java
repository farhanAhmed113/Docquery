package com.docquery.repository;

import com.docquery.model.Document;
import com.docquery.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByOwnerOrderByUploadedAtDesc(User owner);
    Optional<Document> findByIdAndOwner(Long id, User owner);
}
