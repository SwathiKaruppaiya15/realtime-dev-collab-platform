package com.example.backend.controller;

import com.example.backend.dto.CodeChangeMessage;
import com.example.backend.dto.CursorMessage;
import com.example.backend.entity.User;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomService roomService;
    private final UserRepository userRepository;

    private static final List<String> CURSOR_COLORS = List.of(
            "#f38ba8", "#fab387", "#a6e3a1", "#89b4fa", "#cba6f7", "#94e2d5", "#f9e2af"
    );

    /**
     * CODE_CHANGE — client sends to /app/code.change
     * Broadcast to /topic/code/{roomId}
     */
    @MessageMapping("/code.change")
    public void handleCodeChange(@Payload CodeChangeMessage message, Principal principal) {
        if (principal == null) return;
        User user = userRepository.findByEmail(principal.getName()).orElse(null);
        if (user == null) return;

        String role = roomService.getUserRoleInRoom(user.getId(), message.getRoomId());
        if ("VIEWER".equals(role)) return; // viewers cannot broadcast

        message.setSenderEmail(user.getEmail());
        messagingTemplate.convertAndSend("/topic/code/" + message.getRoomId(), message);
    }

    /**
     * CURSOR_MOVE — client sends to /app/cursor.move
     * Broadcast to /topic/cursor/{roomId}
     */
    @MessageMapping("/cursor.move")
    public void handleCursorMove(@Payload CursorMessage message, Principal principal) {
        if (principal == null) return;
        User user = userRepository.findByEmail(principal.getName()).orElse(null);
        if (user == null) return;

        roomService.getUserRoleInRoom(user.getId(), message.getRoomId());

        message.setUserId(user.getId().toString());
        message.setUserName(user.getName());
        int colorIndex = (int)(user.getId() % CURSOR_COLORS.size());
        message.setColor(CURSOR_COLORS.get(colorIndex));

        messagingTemplate.convertAndSend("/topic/cursor/" + message.getRoomId(), message);
    }

    /**
     * FILE_OPEN — notify others which file a user opened
     * Client sends to /app/file.open
     * Broadcast to /topic/file/{roomId}
     */
    @MessageMapping("/file.open")
    public void handleFileOpen(@Payload Map<String, Object> payload, Principal principal) {
        if (principal == null) return;
        User user = userRepository.findByEmail(principal.getName()).orElse(null);
        if (user == null) return;

        Long roomId = Long.valueOf(payload.get("roomId").toString());
        roomService.getUserRoleInRoom(user.getId(), roomId);

        Map<String, Object> event = Map.of(
                "userId", user.getId(),
                "userName", user.getName(),
                "fileId", payload.get("fileId"),
                "fileName", payload.get("fileName")
        );

        messagingTemplate.convertAndSend("/topic/file/" + roomId, event);
    }
}
