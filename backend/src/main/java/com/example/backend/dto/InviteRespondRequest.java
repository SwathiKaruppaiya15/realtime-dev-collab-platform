package com.example.backend.dto;

import lombok.Data;

@Data
public class InviteRespondRequest {
    private Long notificationId;
    private String action; // ACCEPT or REJECT
}
