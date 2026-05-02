package com.example.backend.dto;

import lombok.Data;

@Data
public class ChangeRoleRequest {
    private Long userId;
    private String role; // ADMIN, EDITOR, VIEWER
}
