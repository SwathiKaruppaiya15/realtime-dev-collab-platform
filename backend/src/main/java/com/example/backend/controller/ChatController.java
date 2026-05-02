package com.example.backend.controller;

import com.example.backend.dto.ChatMessageDto;
import com.example.backend.dto.ChatSendRequest;
import com.example.backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * REST: GET /api/chat/{roomId} — load chat history
     */
    @GetMapping("/api/chat/{roomId}")
    @ResponseBody
    public ResponseEntity<List<ChatMessageDto>> getHistory(@PathVariable Long roomId, Principal principal) {
        return ResponseEntity.ok(chatService.getHistory(roomId, principal.getName()));
    }

    /**
     * WebSocket: client sends to /app/chat.send
     * Broadcasts to /topic/room/{roomId}
     */
    @MessageMapping("/chat.send")
    public void handleChat(@Payload ChatSendRequest request, Principal principal) {
        if (principal == null || request.getContent() == null || request.getContent().isBlank()) return;

        ChatMessageDto saved = chatService.saveMessage(
                request.getRoomId(),
                principal.getName(),
                request.getContent()
        );

        messagingTemplate.convertAndSend("/topic/room/" + request.getRoomId(), saved);
    }
}
