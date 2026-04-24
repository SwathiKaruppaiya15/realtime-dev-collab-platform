package com.example.backend.controller;

import com.example.backend.config.AuthHelper;
import com.example.backend.dto.FileNodeResponse;
import com.example.backend.entity.User;
import com.example.backend.service.FileNodeService;
import com.example.backend.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final FileNodeService fileNodeService;
    private final RoomService roomService;
    private final AuthHelper authHelper;

    @GetMapping("/{roomId}")
    public ResponseEntity<byte[]> exportRoom(@PathVariable Long roomId) throws Exception {
        User user = authHelper.getCurrentUser();
        roomService.getUserRoleInRoom(user.getId(), roomId); // validate membership

        List<FileNodeResponse> tree = fileNodeService.getFileTree(roomId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            writeToZip(zos, tree, "");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"room-" + roomId + ".zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(baos.toByteArray());
    }

    private void writeToZip(ZipOutputStream zos, List<FileNodeResponse> nodes, String path) throws Exception {
        for (FileNodeResponse node : nodes) {
            String fullPath = path.isEmpty() ? node.getName() : path + "/" + node.getName();

            if ("folder".equals(node.getType())) {
                zos.putNextEntry(new ZipEntry(fullPath + "/"));
                zos.closeEntry();
                writeToZip(zos, node.getChildren(), fullPath);
            } else {
                zos.putNextEntry(new ZipEntry(fullPath));
                String content = node.getContent() != null ? node.getContent() : "";
                zos.write(content.getBytes());
                zos.closeEntry();
            }
        }
    }
}
