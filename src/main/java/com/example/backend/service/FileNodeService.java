package com.example.backend.service;

import com.example.backend.config.AuthHelper;
import com.example.backend.dto.FileNodeRequest;
import com.example.backend.dto.FileNodeResponse;
import com.example.backend.entity.FileNode;
import com.example.backend.entity.Room;
import com.example.backend.entity.User;
import com.example.backend.repository.FileNodeRepository;
import com.example.backend.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FileNodeService {

    private final FileNodeRepository fileNodeRepository;
    private final RoomRepository roomRepository;
    private final RoomService roomService;
    private final AuthHelper authHelper;

    public FileNodeResponse createNode(FileNodeRequest request) {
        User user = authHelper.getCurrentUser();
        String role = roomService.getUserRoleInRoom(user.getId(), request.getRoomId());

        if ("VIEWER".equals(role)) {
            throw new RuntimeException("Viewers cannot create files");
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new RuntimeException("Room not found"));

        FileNode node = new FileNode();
        node.setName(request.getName());
        node.setType(request.getType() != null ? request.getType().toUpperCase() : "FILE");
        node.setContent(request.getContent());
        node.setParentId(request.getParentId());
        node.setCreatedBy(user.getId());
        node.setRoom(room);

        // auto-detect extension from filename
        String ext = extractExtension(request.getName(), node.getType());
        node.setExtension(ext);

        fileNodeRepository.save(node);
        return toResponse(node);
    }

    public FileNodeResponse updateNode(Long id, FileNodeRequest request) {
        User user = authHelper.getCurrentUser();
        FileNode node = fileNodeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("File not found"));

        String role = roomService.getUserRoleInRoom(user.getId(), node.getRoom().getId());

        if ("VIEWER".equals(role)) {
            throw new RuntimeException("Viewers cannot edit files");
        }

        // rename support
        if (request.getName() != null && !request.getName().isBlank()) {
            node.setName(request.getName());
            node.setExtension(extractExtension(request.getName(), node.getType()));
        }
        if (request.getContent() != null) {
            node.setContent(request.getContent());
        }

        fileNodeRepository.save(node);
        return toResponse(node);
    }

    public void deleteNode(Long id) {
        User user = authHelper.getCurrentUser();
        FileNode node = fileNodeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("File not found"));

        String role = roomService.getUserRoleInRoom(user.getId(), node.getRoom().getId());

        if (!"ADMIN".equals(role)) {
            throw new RuntimeException("Only ADMIN can delete files");
        }

        // also delete all children if it's a folder
        deleteRecursive(id);
    }

    private void deleteRecursive(Long nodeId) {
        List<FileNode> children = fileNodeRepository.findByParentId(nodeId);
        for (FileNode child : children) {
            deleteRecursive(child.getId());
        }
        fileNodeRepository.deleteById(nodeId);
    }

    public List<FileNodeResponse> getFileTree(Long roomId) {
        User user = authHelper.getCurrentUser();
        roomService.getUserRoleInRoom(user.getId(), roomId); // validates membership

        List<FileNode> all = fileNodeRepository.findByRoomId(roomId);
        List<FileNodeResponse> flat = all.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return buildTree(flat, null);
    }

    private List<FileNodeResponse> buildTree(List<FileNodeResponse> all, Long parentId) {
        return all.stream()
                .filter(n -> parentId == null
                        ? n.getParentId() == null
                        : parentId.equals(n.getParentId()))
                .map(n -> {
                    n.setChildren(buildTree(all, n.getId()));
                    return n;
                })
                .collect(Collectors.toList());
    }

    private String extractExtension(String name, String type) {
        if (name == null || "FOLDER".equalsIgnoreCase(type)) return null;
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : null;
    }

    private FileNodeResponse toResponse(FileNode node) {
        FileNodeResponse r = new FileNodeResponse();
        r.setId(node.getId());
        r.setName(node.getName());
        r.setType(node.getType());
        r.setContent(node.getContent());
        r.setExtension(node.getExtension());
        r.setParentId(node.getParentId());
        r.setRoomId(node.getRoom().getId());
        r.setCreatedBy(node.getCreatedBy());
        return r;
    }
}
