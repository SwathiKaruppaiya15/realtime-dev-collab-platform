package com.example.backend.dto;

import lombok.Data;

@Data
public class InviteRequest {
    private Long roomId;
    private String email;  // invite by email
    private String role;   // EDITOR or VIEWER
}
