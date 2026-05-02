package com.example.backend.controller;

import com.example.backend.dto.InviteRespondRequest;
import com.example.backend.dto.InviteSendRequest;
import com.example.backend.dto.NotificationResponse;
import com.example.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // Send invite notification (ADMIN only)
    @PostMapping("/invite/send")
    public ResponseEntity<NotificationResponse> send(@RequestBody InviteSendRequest request) {
        return ResponseEntity.ok(notificationService.sendInvite(request));
    }

    // Get all notifications for logged-in user
    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationResponse>> getAll() {
        return ResponseEntity.ok(notificationService.getMyNotifications());
    }

    // Get unread (PENDING) count
    @GetMapping("/notifications/count")
    public ResponseEntity<Map<String, Long>> getCount() {
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount()));
    }

    // Accept or reject an invite
    @PostMapping("/invite/respond")
    public ResponseEntity<NotificationResponse> respond(@RequestBody InviteRespondRequest request) {
        return ResponseEntity.ok(notificationService.respond(request));
    }
}
