package com.example.backend.controller;

import com.example.backend.dto.TerminalCommand;
import com.example.backend.service.TerminalService;
import com.example.backend.service.TerminalSessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class TerminalController {

    private final TerminalService terminalService;
    private final TerminalSessionManager sessionManager;

    /**
     * Client sends command to /app/terminal.command
     * Output streams back to /topic/terminal/{sessionId}
     *
     * sessionId = roomId:userEmail  (unique per user per room)
     */
    @MessageMapping("/terminal.command")
    public void handleCommand(@Payload TerminalCommand payload, Principal principal) {
        if (principal == null || payload.getRoomId() == null) return;

        String sessionId = sessionManager.getSessionId(payload.getRoomId(), principal.getName());
        terminalService.executeCommand(sessionId, payload.getCommand());
    }

    /**
     * Client sends to /app/terminal.connect to get their sessionId back
     * so they know which topic to subscribe to
     */
    @MessageMapping("/terminal.connect")
    public void handleConnect(@Payload TerminalCommand payload, Principal principal) {
        if (principal == null || payload.getRoomId() == null) return;

        String sessionId = sessionManager.getSessionId(payload.getRoomId(), principal.getName());
        String workingDir = sessionManager.getWorkingDir(sessionId);

        // Send welcome message
        terminalService.executeCommand(sessionId, "echo 'Terminal ready. Working dir: " + workingDir + "'");
    }
}
