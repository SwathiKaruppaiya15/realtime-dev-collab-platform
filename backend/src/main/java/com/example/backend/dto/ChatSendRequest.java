package com.example.backend.dto;

import lombok.Data;

@Data
public class ChatSendRequest {
    private Long roomId;
    private String content;
}
