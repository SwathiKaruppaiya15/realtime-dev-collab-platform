package com.example.backend.service;

import com.example.backend.config.AuthHelper;
import com.example.backend.dto.InviteRespondRequest;
import com.example.backend.dto.InviteSendRequest;
import com.example.backend.dto.NotificationResponse;
import com.example.backend.entity.*;
import com.example.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final AuthHelper authHelper;

    @Transactional
    public NotificationResponse sendInvite(InviteSendRequest request) {
        User sender = authHelper.getCurrentUser();

        // Validate sender is ADMIN of the room
        RoomMember senderMember = roomMemberRepository
                .findByUserIdAndRoomId(sender.getId(), request.getRoomId())
                .orElseThrow(() -> new RuntimeException("You are not a member of this room"));
        if (!"ADMIN".equals(senderMember.getRole())) {
            throw new RuntimeException("Only ADMIN can send invites");
        }

        // Validate receiver exists
        User receiver = userRepository.findByEmail(request.getReceiverEmail())
                .orElseThrow(() -> new RuntimeException("User not found: " + request.getReceiverEmail()));

        // Don't invite yourself
        if (sender.getId().equals(receiver.getId())) {
            throw new RuntimeException("You cannot invite yourself");
        }

        // Don't invite existing members
        if (roomMemberRepository.existsByUserIdAndRoomId(receiver.getId(), request.getRoomId())) {
            throw new RuntimeException("User is already a member of this room");
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new RuntimeException("Room not found"));

        Notification notification = new Notification();
        notification.setSender(sender);
        notification.setReceiver(receiver);
        notification.setRoom(room);
        notification.setType("INVITE");
        notification.setStatus("PENDING");
        notificationRepository.save(notification);

        return toResponse(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications() {
        User user = authHelper.getCurrentUser();
        return notificationRepository
                .findByReceiverIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public long getUnreadCount() {
        User user = authHelper.getCurrentUser();
        return notificationRepository.countByReceiverIdAndStatus(user.getId(), "PENDING");
    }

    @Transactional
    public NotificationResponse respond(InviteRespondRequest request) {
        User user = authHelper.getCurrentUser();

        Notification notification = notificationRepository.findById(request.getNotificationId())
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!notification.getReceiver().getId().equals(user.getId())) {
            throw new RuntimeException("This notification is not for you");
        }
        if (!"PENDING".equals(notification.getStatus())) {
            throw new RuntimeException("Notification already responded to");
        }

        String action = request.getAction().toUpperCase();
        if ("ACCEPT".equals(action)) {
            notification.setStatus("ACCEPTED");

            // Add user to room as EDITOR
            if (!roomMemberRepository.existsByUserIdAndRoomId(user.getId(), notification.getRoom().getId())) {
                RoomMember member = new RoomMember();
                member.setUser(user);
                member.setRoom(notification.getRoom());
                member.setRole("EDITOR");
                roomMemberRepository.save(member);
            }
        } else if ("REJECT".equals(action)) {
            notification.setStatus("REJECTED");
        } else {
            throw new RuntimeException("Invalid action. Use ACCEPT or REJECT");
        }

        notificationRepository.save(notification);
        return toResponse(notification);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getSender().getName(),
                n.getSender().getEmail(),
                n.getRoom().getId(),
                n.getRoom().getName(),
                n.getType(),
                n.getStatus(),
                n.getCreatedAt().toString()
        );
    }
}
