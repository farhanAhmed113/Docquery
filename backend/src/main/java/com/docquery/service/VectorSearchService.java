package com.docquery.service;

import com.docquery.model.Chunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * In-memory cosine-similarity retrieval over a document's chunks.
 *
 * Scoped intentionally: for a fresher/portfolio-scale project (a handful of
 * documents, a few hundred chunks each) loading a document's chunk vectors
 * into memory and scoring them is fast and simple. At production scale this
 * is exactly the point where you'd swap in a dedicated vector DB
 * (pgvector, Pinecone, Qdrant, etc.) behind the same interface.
 */
@Service
public class VectorSearchService {

    private final EmbeddingService embeddingService;
    private final int topK;

    public VectorSearchService(EmbeddingService embeddingService,
                                @Value("${app.retrieval.top-k}") int topK) {
        this.embeddingService = embeddingService;
        this.topK = topK;
    }

    public record ScoredChunk(Chunk chunk, double score) {}

    public List<ScoredChunk> topMatches(double[] queryVector, List<Chunk> candidates) {
        return candidates.stream()
                .map(c -> new ScoredChunk(c, cosineSimilarity(queryVector, embeddingService.fromJson(c.getEmbedding()))))
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(topK)
                .toList();
    }

    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, normA = 0, normB = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
