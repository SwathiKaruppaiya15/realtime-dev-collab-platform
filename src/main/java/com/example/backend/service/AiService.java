package com.example.backend.service;

import com.example.backend.dto.AiRequest;
import com.example.backend.dto.AiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AiService {

    private static final String OPENAI_URL  = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL       = "gpt-4o-mini";
    private static final int    MAX_TOKENS  = 2000;
    private static final int    MAX_RETRIES = 2;          // max 2 retries after first attempt
    private static final long   RETRY_DELAY = 1500L;      // 1.5s between retries

    // Global call counter — helps detect duplicate calls in logs
    private static final AtomicInteger callCounter = new AtomicInteger(0);

    @Value("${openai.api.key:MISSING}")
    private String apiKey;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;

    @PostConstruct
    public void init() {
        String trimmed = apiKey != null ? apiKey.trim() : "";
        if (trimmed.isBlank() || "MISSING".equals(trimmed)) {
            System.out.println("[AiService] ⚠ WARNING: OpenAI API key is NOT configured!");
        } else {
            System.out.println("[AiService] ✓ OpenAI API key loaded: " + trimmed.substring(0, Math.min(8, trimmed.length())) + "...");
        }
    }

    // ─── Generate mode ───────────────────────────────────────────────────────

    public AiResponse generate(AiRequest request) {
        validateKey();
        validateRequest(request);

        String lang = nonBlank(request.getLanguage(), "the appropriate language");

        String systemPrompt = "You are a coding assistant. " +
                "Generate only clean, correct, complete " + lang + " code. " +
                "Return ONLY the raw code — no explanations, no markdown fences, no extra text. " +
                "For Java, use 'Main' as the public class name unless specified otherwise.";

        StringBuilder userPrompt = new StringBuilder();
        if (nonBlank(request.getFileName())) userPrompt.append("File: ").append(request.getFileName()).append("\n");
        if (nonBlank(request.getLanguage())) userPrompt.append("Language: ").append(request.getLanguage()).append("\n");

        if (nonBlank(request.getExistingCode())) {
            userPrompt.append("\nExisting code:\n").append(request.getExistingCode())
                      .append("\n\nInstruction: ").append(request.getPrompt());
        } else {
            userPrompt.append("\nTask: ").append(request.getPrompt());
        }

        return callOpenAIWithRetry(systemPrompt, userPrompt.toString());
    }

    // ─── Improve mode ────────────────────────────────────────────────────────

    public AiResponse improve(AiRequest request) {
        validateKey();
        validateRequest(request);

        if (!nonBlank(request.getExistingCode())) {
            throw new RuntimeException("No file content provided. Please select a file first.");
        }

        String lang = nonBlank(request.getLanguage(), "the appropriate language");

        String systemPrompt = "You are a coding assistant modifying an existing " + lang + " file. " +
                "Return ONLY the complete modified code — no explanations, no markdown fences. " +
                "Preserve the overall structure unless the instruction says otherwise.";

        String userPrompt = "File: " + nonBlank(request.getFileName(), "file") + "\n" +
                "Language: " + lang + "\n\n" +
                "Existing code:\n" + request.getExistingCode() + "\n\n" +
                "Instruction: " + request.getPrompt();

        return callOpenAIWithRetry(systemPrompt, userPrompt);
    }

    // ─── OpenAI call with retry ───────────────────────────────────────────────

    /**
     * Calls OpenAI with up to MAX_RETRIES retries on transient errors.
     * 429 (rate limit) is retried after RETRY_DELAY.
     * 401 (bad key) is NOT retried — it will never succeed.
     */
    private AiResponse callOpenAIWithRetry(String systemPrompt, String userPrompt) {
        int callId = callCounter.incrementAndGet();
        System.out.println("[AI] ─── API CALLED (call #" + callId + ") ───────────────────");
        System.out.println("[AI] Model: " + MODEL + " | Prompt length: " + userPrompt.length() + " chars");

        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES + 1; attempt++) {
            if (attempt > 1) {
                System.out.println("[AI] Retry attempt " + attempt + "/" + (MAX_RETRIES + 1) + " after " + RETRY_DELAY + "ms...");
                try {
                    Thread.sleep(RETRY_DELAY);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("AI call interrupted");
                }
            }

            try {
                AiResponse result = callOpenAI(systemPrompt, userPrompt, callId, attempt);
                System.out.println("[AI] ✓ Success on attempt " + attempt + " (call #" + callId + ")");
                return result;

            } catch (RuntimeException e) {
                lastException = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";

                // 401 — wrong key, retrying won't help
                if (msg.contains("401") || msg.contains("Unauthorized")) {
                    System.err.println("[AI] ✗ 401 Unauthorized — not retrying (call #" + callId + ")");
                    throw e;
                }

                // 429 — rate limit, retry after delay
                if (msg.contains("429") || msg.contains("rate limit")) {
                    System.err.println("[AI] ⚠ 429 Rate limit on attempt " + attempt + " (call #" + callId + ")");
                    if (attempt <= MAX_RETRIES) continue; // will sleep and retry
                }

                // Other errors — retry once
                System.err.println("[AI] ✗ Error on attempt " + attempt + ": " + msg);
                if (attempt <= MAX_RETRIES) continue;
            }
        }

        System.err.println("[AI] ✗ All " + (MAX_RETRIES + 1) + " attempts failed (call #" + callId + ")");
        throw lastException != null ? lastException
                : new RuntimeException("AI call failed after " + (MAX_RETRIES + 1) + " attempts");
    }

    private AiResponse callOpenAI(String systemPrompt, String userPrompt, int callId, int attempt) {
        System.out.println("[AI] → Sending to OpenAI (call #" + callId + ", attempt " + attempt + ")");

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", MODEL);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user",   "content", userPrompt)
        ));
        body.put("max_tokens", MAX_TOKENS);
        body.put("temperature", 0.2);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey.trim());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(OPENAI_URL, entity, String.class);
            System.out.println("[AI] ← Response received, HTTP " + response.getStatusCode());
            String code = extractCode(response.getBody());
            System.out.println("[AI] ← Code extracted, length=" + code.length() + " chars");
            return new AiResponse(code);

        } catch (HttpClientErrorException.Unauthorized e) {
            throw new RuntimeException("OpenAI rejected the API key (401). Verify your key at platform.openai.com");
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new RuntimeException("OpenAI rate limit hit (429). Wait and try again.");
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("OpenAI API error " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new RuntimeException("AI call failed: " + e.getMessage());
        }
    }

    // ─── Response parsing ────────────────────────────────────────────────────

    private String extractCode(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (choices.isMissingNode() || choices.isEmpty()) {
            throw new RuntimeException("OpenAI returned no choices");
        }
        String raw = choices.get(0).path("message").path("content").asText("").trim();
        if (raw.isBlank()) {
            throw new RuntimeException("OpenAI returned empty content");
        }
        // Strip markdown fences if model added them despite instructions
        raw = raw.replaceAll("(?s)^```[\\w]*\\r?\\n?", "")
                 .replaceAll("(?s)\\r?\\n?```\\s*$", "")
                 .trim();
        return raw;
    }

    // ─── Validation ──────────────────────────────────────────────────────────

    private void validateKey() {
        String trimmed = apiKey != null ? apiKey.trim() : "";
        if (trimmed.isBlank() || "MISSING".equals(trimmed)) {
            throw new RuntimeException("OpenAI API key not configured. Set openai.api.key in application.properties.");
        }
    }

    private void validateRequest(AiRequest request) {
        if (request == null || request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new RuntimeException("Prompt cannot be empty");
        }
        if (request.getPrompt().length() > 3000) {
            throw new RuntimeException("Prompt too long (max 3000 characters)");
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String nonBlank(String s, String fallback) {
        return (s != null && !s.isBlank()) ? s : fallback;
    }
}
