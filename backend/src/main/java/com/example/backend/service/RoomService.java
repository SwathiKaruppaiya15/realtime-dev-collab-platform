package com.example.backend.service;

import com.example.backend.config.AuthHelper;
import com.example.backend.dto.*;
import com.example.backend.entity.Room;
import com.example.backend.entity.RoomMember;
import com.example.backend.entity.User;
import com.example.backend.repository.FileNodeRepository;
import com.example.backend.repository.RoomMemberRepository;
import com.example.backend.repository.RoomRepository;
import com.example.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final FileNodeRepository fileNodeRepository;
    private final UserRepository userRepository;
    private final AuthHelper authHelper;

    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {
        User user = authHelper.getCurrentUser();
        String code = generateUniqueCode();

        Room room = new Room();
        room.setName(request.getName());
        room.setCode(code);
        room.setOwner(user);
        roomRepository.save(room);

        RoomMember member = new RoomMember();
        member.setUser(user);
        member.setRoom(room);
        member.setRole("ADMIN");
        roomMemberRepository.save(member);

        return new RoomResponse(room.getId(), room.getName(), room.getCode(), "ADMIN");
    }

    @Transactional
    public RoomResponse joinRoom(JoinRoomRequest request) {
        User user = authHelper.getCurrentUser();
        return doJoin(user, request.getCode());
    }

    @Transactional
    public RoomResponse joinByCode(String code) {
        User user = authHelper.getCurrentUser();
        return doJoin(user, code);
    }

    private RoomResponse doJoin(User user, String code) {
        Room room = roomRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Room not found with code: " + code));

        if (roomMemberRepository.existsByUserIdAndRoomId(user.getId(), room.getId())) {
            String role = roomMemberRepository.findByUserIdAndRoomId(user.getId(), room.getId())
                    .map(RoomMember::getRole).orElse("VIEWER");
            return new RoomResponse(room.getId(), room.getName(), room.getCode(), role);
        }

        RoomMember member = new RoomMember();
        member.setUser(user);
        member.setRoom(room);
        member.setRole("VIEWER");
        roomMemberRepository.save(member);

        return new RoomResponse(room.getId(), room.getName(), room.getCode(), "VIEWER");
    }

    // @Transactional ensures lazy-loaded Room and User inside RoomMember are accessible
    @Transactional(readOnly = true)
    public List<RoomResponse> getMyRooms() {
        User user = authHelper.getCurrentUser();
        List<RoomMember> members = roomMemberRepository.findByUserId(user.getId());

        return members.stream()
                .map(m -> new RoomResponse(
                        m.getRoom().getId(),
                        m.getRoom().getName(),
                        m.getRoom().getCode(),
                        m.getRole()
                ))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RoomMemberResponse> getRoomMembers(Long roomId) {
        User user = authHelper.getCurrentUser();
        getUserRoleInRoom(user.getId(), roomId);

        return roomMemberRepository.findByRoomId(roomId).stream()
                .map(m -> new RoomMemberResponse(
                        m.getUser().getId(),
                        m.getUser().getName(),
                        m.getUser().getEmail(),
                        m.getRole()
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public RoomMemberResponse inviteUser(InviteRequest request) {
        User admin = authHelper.getCurrentUser();
        String adminRole = getUserRoleInRoom(admin.getId(), request.getRoomId());

        if (!"ADMIN".equals(adminRole)) {
            throw new RuntimeException("Only ADMIN can invite users");
        }

        User invitee = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found with email: " + request.getEmail()));

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (roomMemberRepository.existsByUserIdAndRoomId(invitee.getId(), room.getId())) {
            throw new RuntimeException("User is already a member of this room");
        }

        String role = (request.getRole() != null) ? request.getRole().toUpperCase() : "VIEWER";
        if (!List.of("ADMIN", "EDITOR", "VIEWER").contains(role)) {
            throw new RuntimeException("Invalid role: " + role);
        }

        RoomMember member = new RoomMember();
        member.setUser(invitee);
        member.setRoom(room);
        member.setRole(role);
        roomMemberRepository.save(member);

        return new RoomMemberResponse(invitee.getId(), invitee.getName(), invitee.getEmail(), role);
    }

    public String getUserRoleInRoom(Long userId, Long roomId) {
        return roomMemberRepository.findByUserIdAndRoomId(userId, roomId)
                .map(RoomMember::getRole)
                .orElseThrow(() -> new RuntimeException("You are not a member of this room"));
    }

    @Transactional
    public void deleteRoom(Long roomId) {
        User user = authHelper.getCurrentUser();
        String role = getUserRoleInRoom(user.getId(), roomId);

        if (!"ADMIN".equals(role)) {
            throw new RuntimeException("Only ADMIN can delete a room");
        }

        fileNodeRepository.deleteByRoomId(roomId);
        roomMemberRepository.deleteAll(roomMemberRepository.findByRoomId(roomId));
        roomRepository.deleteById(roomId);
    }

    @Transactional
    public void leaveRoom(Long roomId) {
        User user = authHelper.getCurrentUser();
        String role = getUserRoleInRoom(user.getId(), roomId);

        List<RoomMember> allMembers = roomMemberRepository.findByRoomId(roomId);

        if ("ADMIN".equals(role)) {
            if (allMembers.size() == 1) {
                // Last member — delete the whole room
                fileNodeRepository.deleteByRoomId(roomId);
                roomMemberRepository.deleteAll(allMembers);
                roomRepository.deleteById(roomId);
                return;
            }
            // Transfer ADMIN to another member
            allMembers.stream()
                    .filter(m -> !m.getUser().getId().equals(user.getId()))
                    .findFirst()
                    .ifPresent(m -> {
                        m.setRole("ADMIN");
                        roomMemberRepository.save(m);
                    });
        }

        // Remove current user from room
        roomMemberRepository.findByUserIdAndRoomId(user.getId(), roomId)
                .ifPresent(roomMemberRepository::delete);
    }

    @Transactional
    public RoomMemberResponse changeRole(Long roomId, ChangeRoleRequest request) {
        User admin = authHelper.getCurrentUser();
        String adminRole = getUserRoleInRoom(admin.getId(), roomId);

        if (!"ADMIN".equals(adminRole)) {
            throw new RuntimeException("Only ADMIN can change roles");
        }

        String newRole = request.getRole().toUpperCase();
        if (!List.of("ADMIN", "EDITOR", "VIEWER").contains(newRole)) {
            throw new RuntimeException("Invalid role: " + newRole);
        }

        // Prevent removing the last ADMIN
        if ("ADMIN".equals(getUserRoleInRoom(request.getUserId(), roomId))
                && !"ADMIN".equals(newRole)) {
            long adminCount = roomMemberRepository.findByRoomId(roomId).stream()
                    .filter(m -> "ADMIN".equals(m.getRole())).count();
            if (adminCount <= 1) {
                throw new RuntimeException("Cannot remove the last ADMIN");
            }
        }

        RoomMember member = roomMemberRepository.findByUserIdAndRoomId(request.getUserId(), roomId)
                .orElseThrow(() -> new RuntimeException("User is not a member of this room"));

        member.setRole(newRole);
        roomMemberRepository.save(member);

        return new RoomMemberResponse(
                member.getUser().getId(),
                member.getUser().getName(),
                member.getUser().getEmail(),
                member.getRole()
        );
    }

    @Transactional
    public void removeMember(Long roomId, Long targetUserId) {
        User admin = authHelper.getCurrentUser();
        String adminRole = getUserRoleInRoom(admin.getId(), roomId);

        if (!"ADMIN".equals(adminRole)) {
            throw new RuntimeException("Only ADMIN can remove members");
        }
        if (admin.getId().equals(targetUserId)) {
            throw new RuntimeException("Use Leave Room to remove yourself");
        }

        RoomMember member = roomMemberRepository.findByUserIdAndRoomId(targetUserId, roomId)
                .orElseThrow(() -> new RuntimeException("User is not a member of this room"));

        roomMemberRepository.delete(member);
    }

    private String generateUniqueCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String code;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt((int) (Math.random() * chars.length())));
            }
            code = sb.toString();
        } while (roomRepository.existsByCode(code));
        return code;
    }
}
