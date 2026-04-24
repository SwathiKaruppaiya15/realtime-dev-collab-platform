package com.example.backend.controller;

import com.example.backend.dto.AiRequest;
import com.example.backend.dto.AiResponse;
import com.example.backend.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody AiRequest request) {
        System.out.println("[AI] API CALLED — generate | prompt: "
                + (request.getPrompt() != null ? request.getPrompt().substring(0, Math.min(60, request.getPrompt().length())) : "null")
                + " | lang: " + request.getLanguage());
        return handleAiCall(() -> aiService.generate(request));
    }

    @PostMapping("/improve")
    public ResponseEntity<?> improve(@RequestBody AiRequest request) {
        System.out.println("[AI] API CALLED — improve | file: " + request.getFileName()
                + " | prompt: " + (request.getPrompt() != null ? request.getPrompt().substring(0, Math.min(60, request.getPrompt().length())) : "null"));
        return handleAiCall(() -> aiService.improve(request));
    }

    private ResponseEntity<?> handleAiCall(java.util.function.Supplier<AiResponse> supplier) {
        try {
            AiResponse response = supplier.get();
            System.out.println("[AI] Success — code length: " + (response.getCode() != null ? response.getCode().length() : 0));
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "AI generation failed";
            System.err.println("[AI] Error: " + msg);

            // 429 — OpenAI rate limit
            if (msg.contains("429") || msg.contains("rate limit") || msg.contains("Rate limit")) {
                return ResponseEntity
                        .status(HttpStatus.TOO_MANY_REQUESTS)   // 429
                        .body(Map.of("error", "Rate limit hit. Please wait and try again."));
            }

            // OpenAI key invalid — this is a SERVER config problem, NOT a user auth problem.
            // Return 503 (Service Unavailable) so the frontend does NOT trigger logout.
            if (msg.contains("401") || msg.contains("Unauthorized") || msg.contains("API key")) {
                return ResponseEntity
                        .status(HttpStatus.SERVICE_UNAVAILABLE)  // 503 — NOT 401
                        .body(Map.of("error", "AI service is not configured. Contact the admin."));
            }

            // Prompt validation errors
            if (msg.contains("Prompt") || msg.contains("empty") || msg.contains("too long")) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)          // 400
                        .body(Map.of("error", msg));
            }

            // All other errors — 500 Internal Server Error
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)    // 500
                    .body(Map.of("error", "AI generation failed: " + msg));
        }
    }
}
