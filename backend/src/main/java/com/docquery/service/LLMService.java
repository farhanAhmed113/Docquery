package com.docquery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class LLMService {

    private static final String SYSTEM_PROMPT = """
            You are DocQuery, a precise document question-answering assistant.
            You will be given excerpts retrieved from a document and a user question.
            Answer ONLY using the provided excerpts. If the excerpts do not contain
            enough information to answer confidently, say so plainly instead of
            guessing.

            Formatting rules for your answer:
            - Write in clear, well-formed sentences and short paragraphs, like a textbook explanation.
            - If listing multiple items (features, technologies, steps), use a markdown bullet list
              with each item on its own line, starting with "- ".
            - Use **bold** only for key terms, not entire sentences.
            - Never cram a list into a single run-on sentence separated by dashes.
            - Keep paragraphs short (2-4 sentences) and add a blank line between distinct ideas.
            """;

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public LLMService(@Value("${app.openai.api-key}") String apiKey,
                       @Value("${app.openai.chat-model}") String model,
                       @Value("${app.openai.base-url}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    public String answer(String question, String contextExcerpts) {
        try {
            String userContent = "Context excerpts:\n" + contextExcerpts + "\n\nQuestion: " + question;

            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", userContent)
                    ),
                    "temperature", 0.2
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                throw new RuntimeException("LLM API error (" + response.statusCode() + "): " + response.body());
            }

            JsonNode root = mapper.readTree(response.body());
            return root.path("choices").get(0).path("message").path("content").asText().trim();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate answer: " + e.getMessage(), e);
        }
    }
}
