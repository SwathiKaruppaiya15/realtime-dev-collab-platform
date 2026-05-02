package com.example.backend.dto;

import lombok.Data;

@Data
public class ExecuteRequest {
    private String fileName;  // e.g. "Main.java", "script.py", "app.js"
    private String code;
    private String language;  // "java", "python", "javascript"
    private Long roomId;      // used to create isolated temp dir per room
}
