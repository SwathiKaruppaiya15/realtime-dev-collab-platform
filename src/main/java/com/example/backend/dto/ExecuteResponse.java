package com.example.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteResponse {
    private String output;   // combined stdout
    private String error;    // stderr / compilation errors
    private String status;   // "SUCCESS" or "ERROR"
    private int exitCode;
}
