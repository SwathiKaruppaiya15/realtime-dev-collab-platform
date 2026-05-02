package com.example.backend.dto;

import lombok.Data;

@Data
public class TerminalCommand {
    private String roomId;
    private String command;
}
