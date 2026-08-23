package com.docquery.repository;

import com.docquery.model.Document;
import com.docquery.model.QaHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QaHistoryRepository extends JpaRepository<QaHistory, Long> {
    List<QaHistory> findByDocumentOrderByAskedAtDesc(Document document);
}
