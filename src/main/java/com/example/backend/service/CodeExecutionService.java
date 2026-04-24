package com.example.backend.service;

import com.example.backend.dto.ExecuteRequest;
import com.example.backend.dto.ExecuteResponse;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;

@Service
public class CodeExecutionService {

    private static final int  TIMEOUT_SECONDS    = 15;
    private static final long MAX_CODE_BYTES      = 100_000;

    // ─── Public entry point ──────────────────────────────────────────────────

    public ExecuteResponse execute(ExecuteRequest request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            return error("Code cannot be empty");
        }
        if (request.getCode().length() > MAX_CODE_BYTES) {
            return error("Code exceeds 100 KB limit");
        }

        String language = request.getLanguage();
        if (language == null || language.isBlank()) {
            language = detectLanguage(request.getFileName());
        }
        language = language.toLowerCase().trim();

        if (language.equals("html") || language.equals("css")) {
            // Handled entirely in the browser — no Docker needed
            return new ExecuteResponse("", "", "HTML_PREVIEW", 0);
        }

        String roomId = request.getRoomId() != null ? request.getRoomId().toString() : "default";
        System.out.println("[Exec] lang=" + language + " room=" + roomId + " file=" + request.getFileName());

        // Step 1: verify Docker is reachable before doing anything else
        ExecuteResponse dockerCheck = verifyDocker();
        if (dockerCheck != null) return dockerCheck;

        try {
            return switch (language) {
                case "java"        -> runJava(request.getCode(), request.getFileName(), roomId);
                case "python"      -> runPython(request.getCode(), request.getFileName(), roomId);
                case "javascript"  -> runJavaScript(request.getCode(), request.getFileName(), roomId);
                case "cpp", "c++"  -> runCpp(request.getCode(), request.getFileName(), roomId);
                case "c"           -> runC(request.getCode(), request.getFileName(), roomId);
                default            -> error("Unsupported language: " + language);
            };
        } catch (Exception e) {
            System.err.println("[Exec] Exception: " + e.getMessage());
            e.printStackTrace();
            return error("Execution engine error: " + e.getMessage());
        }
    }

    // ─── Language runners ────────────────────────────────────────────────────

    private ExecuteResponse runJava(String code, String fileName, String roomId) throws Exception {
        String className = (fileName != null && fileName.endsWith(".java"))
                ? fileName.replace(".java", "") : "Main";

        String finalCode = code.contains("class " + className) ? code
                : "public class " + className + " {\n"
                + "    public static void main(String[] args) throws Exception {\n"
                + code + "\n    }\n}";

        Path dir = createTempDir(roomId);
        // Write WITHOUT BOM — Windows Files.writeString uses UTF-8 but may add BOM
        writeUtf8NoBom(dir.resolve(className + ".java"), finalCode);

        String bashCmd = "javac " + className + ".java && java " + className;
        return runInDocker(dir, "amazoncorretto:17", bashCmd);
    }

    private ExecuteResponse runPython(String code, String fileName, String roomId) throws Exception {
        String file = (fileName != null && fileName.endsWith(".py")) ? fileName : "main.py";
        Path dir = createTempDir(roomId);
        writeUtf8NoBom(dir.resolve(file), code);
        return runInDocker(dir, "python:3.11-slim", "python " + file);
    }

    private ExecuteResponse runJavaScript(String code, String fileName, String roomId) throws Exception {
        String file = (fileName != null && fileName.endsWith(".js")) ? fileName : "main.js";
        Path dir = createTempDir(roomId);
        writeUtf8NoBom(dir.resolve(file), code);
        return runInDocker(dir, "node:18-slim", "node " + file);
    }

    private ExecuteResponse runCpp(String code, String fileName, String roomId) throws Exception {
        String file = (fileName != null && (fileName.endsWith(".cpp") || fileName.endsWith(".cc")))
                ? fileName : "main.cpp";
        Path dir = createTempDir(roomId);
        writeUtf8NoBom(dir.resolve(file), code);
        return runInDocker(dir, "gcc:latest", "g++ -o out " + file + " && ./out");
    }

    private ExecuteResponse runC(String code, String fileName, String roomId) throws Exception {
        String file = (fileName != null && fileName.endsWith(".c")) ? fileName : "main.c";
        Path dir = createTempDir(roomId);
        writeUtf8NoBom(dir.resolve(file), code);
        return runInDocker(dir, "gcc:latest", "gcc -o out " + file + " && ./out");
    }

    // ─── Core Docker runner ──────────────────────────────────────────────────

    /**
     * Runs code inside a Docker container.
     *
     * KEY WINDOWS FIX: We pass the entire docker command as a single string
     * to cmd.exe /c so Windows can resolve the docker binary from PATH.
     *
     * KEY PATH FIX: Windows paths like C:\Users\foo\bar must become
     * /c/Users/foo/bar for Docker volume mounts.
     */
    private ExecuteResponse runInDocker(Path dir, String image, String containerCmd) throws Exception {
        String hostPath = toDockerPath(dir.toAbsolutePath().toString());

        // Build the full docker command as a single string
        // Using bash -c inside the container so && works for compile+run
        String dockerCmd = String.format(
                "docker run --rm --platform=linux/amd64 "
                + "--memory=256m --cpus=0.5 --network=none --pids-limit=64 "
                + "-v \"%s:/app\" -w /app %s bash -c \"%s\"",
                hostPath, image, containerCmd.replace("\"", "\\\"")
        );

        System.out.println("[Docker] Command: " + dockerCmd);
        System.out.println("[Docker] Host path: " + hostPath);

        // On Windows: cmd.exe /c <command>
        // This ensures docker is found via PATH and the command string is parsed correctly
        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", dockerCmd);
        pb.redirectErrorStream(false); // keep stdout and stderr separate
        pb.directory(dir.toFile());    // set working dir to temp folder

        Process process = pb.start();
        System.out.println("[Docker] Process started, PID=" + process.pid());

        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<String> stdoutFuture = executor.submit(() ->
                readStream(process.getInputStream()));
        Future<String> stderrFuture = executor.submit(() ->
                readStream(process.getErrorStream()));

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            executor.shutdownNow();
            cleanup(dir);
            System.err.println("[Docker] TIMEOUT after " + TIMEOUT_SECONDS + "s");
            return error("Execution timed out after " + TIMEOUT_SECONDS + "s — check for infinite loops");
        }

        String stdout  = stdoutFuture.get(5, TimeUnit.SECONDS);
        String stderr  = stderrFuture.get(5, TimeUnit.SECONDS);
        int    exitCode = process.exitValue();

        executor.shutdown();
        cleanup(dir);

        System.out.println("[Docker] Exit code: " + exitCode);
        if (!stdout.isBlank()) System.out.println("[Docker] stdout: " + stdout.substring(0, Math.min(200, stdout.length())));
        if (!stderr.isBlank()) System.err.println("[Docker] stderr: " + stderr.substring(0, Math.min(200, stderr.length())));

        if (exitCode == 0) {
            return new ExecuteResponse(stdout, stderr, "SUCCESS", exitCode);
        } else {
            // Prefer stderr for error message; fall back to stdout (e.g. javac merges both)
            String errorMsg = stderr.isBlank() ? stdout : stderr;
            return new ExecuteResponse(stdout, errorMsg, "ERROR", exitCode);
        }
    }

    // ─── Docker health check ─────────────────────────────────────────────────

    /**
     * Runs "docker run --rm hello-world" to verify Docker is working.
     * Returns null if Docker is fine, or an error response if not.
     */
    private ExecuteResponse verifyDocker() {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "docker info");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean ok = p.waitFor(8, TimeUnit.SECONDS);
            if (!ok || p.exitValue() != 0) {
                String out = readStream(p.getInputStream());
                System.err.println("[Docker] docker info failed: " + out);
                return error("Docker is not running or not accessible. Start Docker Desktop and try again.");
            }
            System.out.println("[Docker] Docker is running OK");
            return null; // all good
        } catch (Exception e) {
            System.err.println("[Docker] Cannot find docker: " + e.getMessage());
            return error("Docker not found. Make sure Docker Desktop is installed and running.");
        }
    }

    // ─── Windows path conversion ─────────────────────────────────────────────

    /**
     * Converts a Windows absolute path to Docker-compatible format.
     *
     * Examples:
     *   C:\Users\foo\bar  →  /c/Users/foo/bar
     *   C:/Users/foo/bar  →  /c/Users/foo/bar
     */
    private String toDockerPath(String windowsPath) {
        // Normalize backslashes to forward slashes
        String path = windowsPath.replace("\\", "/");

        // Convert drive letter: C:/... → /c/...
        if (path.length() >= 2 && path.charAt(1) == ':') {
            char drive = Character.toLowerCase(path.charAt(0));
            path = "/" + drive + path.substring(2);
        }

        System.out.println("[Docker] Converted path: " + windowsPath + " → " + path);
        return path;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Write file as UTF-8 WITHOUT BOM.
     * Windows Files.writeString can produce BOM which breaks Java compilation inside Docker.
     */
    private void writeUtf8NoBom(Path path, String content) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(path.toFile()), java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    private Path createTempDir(String roomId) throws IOException {
        Path dir = Paths.get(System.getProperty("java.io.tmpdir"),
                "coderoom", "room-" + roomId, UUID.randomUUID().toString())
                .toAbsolutePath();
        Files.createDirectories(dir);
        System.out.println("[Exec] Temp dir: " + dir);
        return dir;
    }

    private String readStream(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private void cleanup(Path dir) {
        try {
            Files.walk(dir)
                 .sorted((a, b) -> -a.compareTo(b))
                 .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }

    private String detectLanguage(String fileName) {
        if (fileName == null) return "python";
        if (fileName.endsWith(".java"))                    return "java";
        if (fileName.endsWith(".py"))                      return "python";
        if (fileName.endsWith(".js"))                      return "javascript";
        if (fileName.endsWith(".cpp") || fileName.endsWith(".cc")) return "cpp";
        if (fileName.endsWith(".c"))                       return "c";
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) return "html";
        if (fileName.endsWith(".css"))                     return "css";
        return "python";
    }

    private ExecuteResponse error(String message) {
        System.err.println("[Exec] Error: " + message);
        return new ExecuteResponse("", message, "ERROR", 1);
    }
}
