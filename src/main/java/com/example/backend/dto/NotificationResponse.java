package com.example.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private String senderName;
    private String senderEmail;
    private Long roomId;
    private String roomName;
    private String type;
    private String status;
    private String createdAt;
}
