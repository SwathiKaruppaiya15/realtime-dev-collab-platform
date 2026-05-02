package com.example.backend.dto;

import lombok.Data;

@Data
public class CodeChangeMessage {
    private Long roomId;
    private Long fileId;
    private String content;
    private String senderEmail; // set by server, not trusted from client
}
