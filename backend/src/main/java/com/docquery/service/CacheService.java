package com.docquery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;

/**
 * Caches question -> answer per document so a repeated question skips the
 * embedding + LLM round trip entirely. LLM calls are the most expensive and
 * slowest part of this system, so this is a deliberate cost/latency control,
 * not just a nice-to-have.
 */
@Service
public class CacheService {

    private static final Duration TTL = Duration.ofHours(24);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public CacheService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public record CachedAnswer(String answer, String sourceSnippet) {}

    public Optional<CachedAnswer> get(Long documentId, String question) {
        try {
            String key = buildKey(documentId, question);
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) return Optional.empty();
            return Optional.of(mapper.readValue(value, CachedAnswer.class));
        } catch (Exception e) {
            // Cache is best-effort; never let a Redis hiccup break the request.
            return Optional.empty();
        }
    }

    public void put(Long documentId, String question, CachedAnswer answer) {
        try {
            String key = buildKey(documentId, question);
            redisTemplate.opsForValue().set(key, mapper.writeValueAsString(answer), TTL);
        } catch (Exception ignored) {
            // Non-fatal: worst case, the next identical question misses the cache.
        }
    }

    private String buildKey(Long documentId, String question) {
        String normalized = question.trim().toLowerCase();
        return "qa:" + documentId + ":" + sha256(normalized);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
