package com.example.backend.controller;

import com.example.backend.dto.FileNodeRequest;
import com.example.backend.dto.FileNodeResponse;
import com.example.backend.service.FileNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileNodeController {

    private final FileNodeService fileNodeService;

    // Create file or folder
    @PostMapping("/create")
    public ResponseEntity<FileNodeResponse> create(@RequestBody FileNodeRequest request) {
        return ResponseEntity.ok(fileNodeService.createNode(request));
    }

    // Update content or rename
    @PutMapping("/update/{id}")
    public ResponseEntity<FileNodeResponse> update(@PathVariable Long id,
                                                    @RequestBody FileNodeRequest request) {
        return ResponseEntity.ok(fileNodeService.updateNode(id, request));
    }

    // Delete file or folder (ADMIN only)
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        fileNodeService.deleteNode(id);
        return ResponseEntity.noContent().build();
    }

    // Get full file tree for a room
    @GetMapping("/{roomId}")
    public ResponseEntity<List<FileNodeResponse>> getTree(@PathVariable Long roomId) {
        return ResponseEntity.ok(fileNodeService.getFileTree(roomId));
    }
}
