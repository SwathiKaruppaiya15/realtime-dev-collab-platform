package com.example.backend.controller;

import com.example.backend.dto.*;
import com.example.backend.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    // Create a new room
    @PostMapping("/create")
    public ResponseEntity<RoomResponse> createRoom(@RequestBody CreateRoomRequest request) {
        return ResponseEntity.ok(roomService.createRoom(request));
    }

    // Join via POST body
    @PostMapping("/join")
    public ResponseEntity<RoomResponse> joinRoom(@RequestBody JoinRoomRequest request) {
        return ResponseEntity.ok(roomService.joinRoom(request));
    }

    // Join via GET URL (share link: /api/rooms/join/A7X2P9)
    @GetMapping("/join/{code}")
    public ResponseEntity<RoomResponse> joinByCode(@PathVariable String code) {
        return ResponseEntity.ok(roomService.joinByCode(code));
    }

    // Get all rooms the current user is in
    @GetMapping("/my")
    public ResponseEntity<List<RoomResponse>> getMyRooms() {
        return ResponseEntity.ok(roomService.getMyRooms());
    }

    // Get all members of a room (ADMIN/EDITOR/VIEWER)
    @GetMapping("/{roomId}/members")
    public ResponseEntity<List<RoomMemberResponse>> getMembers(@PathVariable Long roomId) {
        return ResponseEntity.ok(roomService.getRoomMembers(roomId));
    }

    // Invite a user to a room (ADMIN only)
    @PostMapping("/invite")
    public ResponseEntity<RoomMemberResponse> invite(@RequestBody InviteRequest request) {
        return ResponseEntity.ok(roomService.inviteUser(request));
    }

    // Delete a room (ADMIN only)
    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable Long roomId) {
        roomService.deleteRoom(roomId);
        return ResponseEntity.noContent().build();
    }

    // Leave a room
    @DeleteMapping("/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(@PathVariable Long roomId) {
        roomService.leaveRoom(roomId);
        return ResponseEntity.noContent().build();
    }

    // Change a member's role (ADMIN only)
    @PutMapping("/{roomId}/role")
    public ResponseEntity<RoomMemberResponse> changeRole(@PathVariable Long roomId,
                                                          @RequestBody ChangeRoleRequest request) {
        return ResponseEntity.ok(roomService.changeRole(roomId, request));
    }

    // Remove a member from room (ADMIN only)
    @DeleteMapping("/{roomId}/member/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long roomId, @PathVariable Long userId) {
        roomService.removeMember(roomId, userId);
        return ResponseEntity.noContent().build();
    }
}
