package com.example.backend.dto;

import lombok.Data;

@Data
public class CursorMessage {
    private Long roomId;
    private Long fileId;
    private int line;
    private int column;
    private String userId;   // set by server
    private String userName; // set by server
    private String color;    // set by server
}
