package com.example.backend.service;

import com.example.backend.dto.ChatMessageDto;
import com.example.backend.entity.ChatMessage;
import com.example.backend.entity.Room;
import com.example.backend.entity.User;
import com.example.backend.repository.ChatMessageRepository;
import com.example.backend.repository.RoomRepository;
import com.example.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomService roomService;

    @Transactional
    public ChatMessageDto saveMessage(Long roomId, String senderEmail, String content) {
        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Validate sender is a room member
        roomService.getUserRoleInRoom(sender.getId(), roomId);

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        ChatMessage msg = new ChatMessage();
        msg.setRoom(room);
        msg.setSender(sender);
        msg.setContent(content.trim());
        chatMessageRepository.save(msg);

        return toDto(msg);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getHistory(Long roomId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Validate membership
        roomService.getUserRoleInRoom(user.getId(), roomId);

        return chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private ChatMessageDto toDto(ChatMessage msg) {
        return new ChatMessageDto(
                msg.getId(),
                msg.getRoom().getId(),
                msg.getSender().getId(),
                msg.getSender().getName(),
                msg.getContent(),
                msg.getCreatedAt().toString()
        );
    }
}
