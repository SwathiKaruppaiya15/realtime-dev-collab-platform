package com.example.backend.controller;

import com.example.backend.dto.DashboardResponse;
import com.example.backend.dto.RoomResponse;
import com.example.backend.service.RoomService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final RoomService roomService;

    @GetMapping
    public ResponseEntity<DashboardData> getDashboard() {
        List<RoomResponse> rooms = roomService.getMyRooms();

        long owned  = rooms.stream().filter(r -> "ADMIN".equals(r.getMyRole())).count();
        long joined = rooms.size() - owned;

        DashboardResponse stats = new DashboardResponse(rooms.size(), (int) owned, (int) joined);

        return ResponseEntity.ok(new DashboardData(stats, rooms));
    }

    // Inner DTO — avoids Map.of() serialization issues
    @Data
    @AllArgsConstructor
    public static class DashboardData {
        private DashboardResponse stats;
        private List<RoomResponse> rooms;
    }
}
