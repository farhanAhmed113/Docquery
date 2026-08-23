package com.docquery.service;

import com.docquery.dto.AskResponse;
import com.docquery.model.Chunk;
import com.docquery.model.Document;
import com.docquery.model.QaHistory;
import com.docquery.model.User;
import com.docquery.repository.ChunkRepository;
import com.docquery.repository.QaHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class QueryService {

    private final ChunkRepository chunkRepository;
    private final QaHistoryRepository qaHistoryRepository;
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final LLMService llmService;
    private final CacheService cacheService;

    public QueryService(ChunkRepository chunkRepository,
                         QaHistoryRepository qaHistoryRepository,
                         EmbeddingService embeddingService,
                         VectorSearchService vectorSearchService,
                         LLMService llmService,
                         CacheService cacheService) {
        this.chunkRepository = chunkRepository;
        this.qaHistoryRepository = qaHistoryRepository;
        this.embeddingService = embeddingService;
        this.vectorSearchService = vectorSearchService;
        this.llmService = llmService;
        this.cacheService = cacheService;
    }

    public AskResponse ask(User user, Document document, String question) {
        if (document.getStatus() != Document.ProcessingStatus.READY) {
            throw new IllegalStateException("Document is not ready yet (status: " + document.getStatus() + ")");
        }

        // 1. Cache check first — cheapest possible path.
        Optional<CacheService.CachedAnswer> cached = cacheService.get(document.getId(), question);
        if (cached.isPresent()) {
            logHistory(document, user, question, cached.get().answer(), cached.get().sourceSnippet(), true);
            return new AskResponse(cached.get().answer(), cached.get().sourceSnippet(), true);
        }

        // 2. Retrieve the most relevant chunks for this question.
        List<Chunk> allChunks = chunkRepository.findByDocument(document);
        if (allChunks.isEmpty()) {
            throw new IllegalStateException("Document has no processed content to search");
        }

        double[] questionVector = embeddingService.embed(question);
        List<VectorSearchService.ScoredChunk> matches = vectorSearchService.topMatches(questionVector, allChunks);

        // Guard against low-confidence retrieval — don't let the LLM hallucinate
        // an answer when nothing in the document is actually relevant.
        boolean hasReasonableMatch = matches.stream().anyMatch(m -> m.score() > 0.15);
        if (!hasReasonableMatch) {
            String noAnswer = "I couldn't find anything in this document that answers that question.";
            logHistory(document, user, question, noAnswer, null, false);
            return new AskResponse(noAnswer, null, false);
        }

        String context = matches.stream()
                .map(m -> m.chunk().getContent())
                .collect(Collectors.joining("\n---\n"));

        // 3. Ask the LLM, grounded only in the retrieved context.
        String answer = llmService.answer(question, context);
        String sourceSnippet = truncate(matches.get(0).chunk().getContent(), 400);

        // 4. Cache + log.
        cacheService.put(document.getId(), question, new CacheService.CachedAnswer(answer, sourceSnippet));
        logHistory(document, user, question, answer, sourceSnippet, false);

        return new AskResponse(answer, sourceSnippet, false);
    }

    private void logHistory(Document document, User user, String question, String answer, String snippet, boolean fromCache) {
        QaHistory history = new QaHistory();
        history.setDocument(document);
        history.setUser(user);
        history.setQuestion(question);
        history.setAnswer(answer);
        history.setSourceSnippet(snippet);
        history.setFromCache(fromCache);
        qaHistoryRepository.save(history);
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
