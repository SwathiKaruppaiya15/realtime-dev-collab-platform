package com.example.backend.controller;

import com.example.backend.dto.ExecuteRequest;
import com.example.backend.dto.ExecuteResponse;
import com.example.backend.service.CodeExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ExecutionController {

    private final CodeExecutionService codeExecutionService;

    /**
     * POST /api/run  (primary endpoint as per spec)
     * POST /api/code/run  (existing endpoint — kept for compatibility)
     * POST /api/execute   (legacy)
     *
     * Request:  { "language": "java", "code": "...", "fileName": "Main.java", "roomId": 1 }
     * Response: { "output": "...", "error": "...", "status": "SUCCESS/ERROR/HTML_PREVIEW", "exitCode": 0 }
     */
    @PostMapping({"/api/run", "/api/code/run", "/api/execute"})
    public ResponseEntity<ExecuteResponse> run(@RequestBody ExecuteRequest request) {
        return ResponseEntity.ok(codeExecutionService.execute(request));
    }
}
