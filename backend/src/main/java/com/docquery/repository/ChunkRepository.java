package com.docquery.repository;

import com.docquery.model.Chunk;
import com.docquery.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {
    List<Chunk> findByDocument(Document document);
    void deleteByDocument(Document document);
}
