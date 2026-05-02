package com.example.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AiResponse {
    private String code;
    private String generatedCode;

    // Single constructor — sets both fields so frontend works with either key
    public AiResponse(String code) {
        this.code = code;
        this.generatedCode = code;
    }
}
