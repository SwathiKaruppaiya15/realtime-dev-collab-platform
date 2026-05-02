package com.example.backend.dto;

import lombok.Data;

@Data
public class AiRequest {
    private String prompt;        // user's instruction
    private String language;      // "java", "python", "javascript" etc.
    private String fileName;      // e.g. "Main.java" — used to give context
    private String existingCode;  // optional — current file content for improve/modify mode
}
