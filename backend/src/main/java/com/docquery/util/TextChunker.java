package com.docquery.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits raw document text into overlapping word-based chunks.
 * Overlap ensures an answer that straddles a chunk boundary is not lost.
 */
@Component
public class TextChunker {

    private final int chunkSizeWords;
    private final int overlapWords;

    public TextChunker(@Value("${app.chunk.size-words}") int chunkSizeWords,
                        @Value("${app.chunk.overlap-words}") int overlapWords) {
        this.chunkSizeWords = chunkSizeWords;
        this.overlapWords = overlapWords;
    }

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        String normalized = text.replaceAll("\\s+", " ").trim();
        String[] words = normalized.split(" ");

        int step = Math.max(1, chunkSizeWords - overlapWords);

        for (int start = 0; start < words.length; start += step) {
            int end = Math.min(start + chunkSizeWords, words.length);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                sb.append(words[i]).append(' ');
            }
            String chunk = sb.toString().trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end == words.length) {
                break;
            }
        }
        return chunks;
    }
}
