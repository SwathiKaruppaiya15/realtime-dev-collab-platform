package com.example.backend.dto;

import lombok.Data;

@Data
public class FileNodeRequest {
    private String name;
    private String type;      // "FILE" or "FOLDER"
    private String content;   // optional, for files
    private String extension; // e.g. "js", "py" — auto-derived if not sent
    private Long parentId;    // null = root
    private Long roomId;
}
