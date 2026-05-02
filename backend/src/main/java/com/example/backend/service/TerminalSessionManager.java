package com.example.backend.service;

import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the current working directory for each terminal session.
 * sessionId = roomId + ":" + userEmail  (one terminal per user per room)
 */
@Component
public class TerminalSessionManager {

    // sessionId → current working directory path
    private final ConcurrentHashMap<String, String> sessionDirs = new ConcurrentHashMap<>();

    public String getSessionId(String roomId, String userEmail) {
        return roomId + ":" + userEmail;
    }

    public String getWorkingDir(String sessionId) {
        return sessionDirs.computeIfAbsent(sessionId, id -> {
            // default working dir = /temp/coderoom/room-{roomId}/
            String roomId = id.split(":")[0];
            Path dir = Paths.get(System.getProperty("java.io.tmpdir"), "coderoom", "room-" + roomId);
            try {
                Files.createDirectories(dir);
            } catch (Exception ignored) {}
            return dir.toAbsolutePath().toString();
        });
    }

    public void setWorkingDir(String sessionId, String newDir) {
        sessionDirs.put(sessionId, newDir);
    }

    public void removeSession(String sessionId) {
        sessionDirs.remove(sessionId);
    }
}
