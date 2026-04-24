package com.example.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class TerminalService {

    private final TerminalSessionManager sessionManager;
    private final SimpMessagingTemplate messagingTemplate;

    private static final int TIMEOUT_SECONDS = 15;

    // Commands that are blocked for security
    private static final Set<String> BLOCKED_COMMANDS = Set.of(
            "rm -rf /", "shutdown", "reboot", "halt", "poweroff",
            "mkfs", "dd if=", ":(){ :|:& };:", "format c:"
    );

    /**
     * Execute a terminal command for a session.
     * Output is streamed line-by-line via WebSocket to /topic/terminal/{sessionId}
     */
    public void executeCommand(String sessionId, String command) {
        if (command == null || command.isBlank()) return;

        String trimmed = command.trim();

        // Security check
        if (isBlocked(trimmed)) {
            sendOutput(sessionId, "[BLOCKED] This command is not allowed.\n");
            return;
        }

        String workingDir = sessionManager.getWorkingDir(sessionId);

        // Handle 'cd' locally — no process needed
        if (trimmed.startsWith("cd")) {
            handleCd(sessionId, trimmed, workingDir);
            return;
        }

        // Handle 'pwd' locally
        if (trimmed.equals("pwd")) {
            sendOutput(sessionId, workingDir + "\n");
            return;
        }

        // Handle 'clear'
        if (trimmed.equals("clear") || trimmed.equals("cls")) {
            sendOutput(sessionId, "\u001b[2J\u001b[H"); // ANSI clear
            return;
        }

        // Run the command in the current working directory
        runCommand(sessionId, trimmed, workingDir);
    }

    private void handleCd(String sessionId, String command, String currentDir) {
        String[] parts = command.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            // cd with no args → go to room root
            sendOutput(sessionId, currentDir + "\n");
            return;
        }

        String target = parts[1].trim();
        Path newPath;

        if (target.equals("..")) {
            newPath = Paths.get(currentDir).getParent();
        } else if (Paths.get(target).isAbsolute()) {
            newPath = Paths.get(target);
        } else {
            newPath = Paths.get(currentDir, target);
        }

        if (newPath == null || !Files.isDirectory(newPath)) {
            sendOutput(sessionId, "cd: " + target + ": No such directory\n");
            return;
        }

        // Security: restrict to room's temp directory
        String roomId = sessionId.split(":")[0];
        String roomRoot = Paths.get(System.getProperty("java.io.tmpdir"), "coderoom", "room-" + roomId)
                .toAbsolutePath().toString();

        if (!newPath.toAbsolutePath().toString().startsWith(roomRoot)) {
            sendOutput(sessionId, "cd: Access denied — cannot navigate outside room directory\n");
            return;
        }

        sessionManager.setWorkingDir(sessionId, newPath.toAbsolutePath().toString());
        sendOutput(sessionId, ""); // no output on success (like real terminal)
    }

    private void runCommand(String sessionId, String command, String workingDir) {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.submit(() -> {
            try {
                ProcessBuilder pb;
                boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

                if (isWindows) {
                    pb = new ProcessBuilder("cmd.exe", "/c", command);
                } else {
                    pb = new ProcessBuilder("bash", "-c", command);
                }

                pb.directory(new File(workingDir));
                pb.redirectErrorStream(true); // merge stderr into stdout for terminal feel

                Process process = pb.start();

                // Stream output line by line in real-time
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sendOutput(sessionId, line + "\n");
                    }
                }

                boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    sendOutput(sessionId, "\n[TIMEOUT] Command exceeded " + TIMEOUT_SECONDS + "s limit\n");
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    sendOutput(sessionId, "\n[Exit code: " + exitCode + "]\n");
                }

            } catch (Exception e) {
                sendOutput(sessionId, "[ERROR] " + e.getMessage() + "\n");
            }
        });

        executor.shutdown();
    }

    private void sendOutput(String sessionId, String output) {
        messagingTemplate.convertAndSend("/topic/terminal/" + sessionId, output);
    }

    private boolean isBlocked(String command) {
        String lower = command.toLowerCase();
        return BLOCKED_COMMANDS.stream().anyMatch(lower::contains);
    }
}
