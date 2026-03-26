package com.incoin.demo.controller;

import com.incoin.demo.dto.SelectToolRequest;
import com.incoin.demo.dto.StartGrabRequest;
import com.incoin.demo.model.GrabConfig;
import com.incoin.demo.model.GrabState;
import com.incoin.demo.model.UserSession;
import com.incoin.demo.service.IncoinApiService;
import com.incoin.demo.service.SessionService;
import com.incoin.demo.service.WorkerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * All grab-related endpoints.
 * Every endpoint requires a valid JWT (enforced by SecurityConfig).
 */
@RestController
@RequestMapping("/grab")
@RequiredArgsConstructor
public class GrabController {

    private final SessionService   sessionService;
    private final IncoinApiService incoinApi;
    private final WorkerService    workerService;

    // ── Tools ─────────────────────────────────────────────────────────────────

    /**
     * GET /grab/tools
     * Returns eligible UPI tools for this user from Incoin.
     */
    @GetMapping("/tools")
    public ResponseEntity<List<Map<String, Object>>> getTools(
        @AuthenticationPrincipal String userId
    ) {
        UserSession session = sessionService.getOrThrow(userId);
        return ResponseEntity.ok(incoinApi.getTools(session));
    }

    /**
     * POST /grab/tool
     * Persist the user's tool choice in their session.
     *
     * Body: { "upiAddr": "...", "toolType": "...", "toolName": "..." }
     */
    @PostMapping("/tool")
    public ResponseEntity<Void> selectTool(
        @AuthenticationPrincipal String userId,
        @Valid @RequestBody SelectToolRequest req
    ) {
        UserSession session = sessionService.getOrThrow(userId);
        session.setSelectedUpiAddr(req.getUpiAddr());
        session.setSelectedToolType(req.getToolType());
        session.setSelectedToolName(req.getToolName());
        sessionService.save(session, userId);
        return ResponseEntity.ok().build();
    }

    // ── Start / Stop ──────────────────────────────────────────────────────────

    /**
     * POST /grab/start
     * Launches a background grab loop for this user.
     * Non-blocking: returns immediately, loop runs in a separate thread.
     *
     * Body: { "minAmount": 400, "maxAmount": 500, "target": 3 }
     */
    @PostMapping("/start")
    public ResponseEntity<Void> start(
        @AuthenticationPrincipal String userId,
        @Valid @RequestBody StartGrabRequest req
    ) {
        UserSession session = sessionService.getOrThrow(userId);

        if (session.getSelectedUpiAddr() == null || session.getSelectedUpiAddr().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No tool selected. Call POST /grab/tool first.");
        }
        if (req.getMinAmount() >= req.getMaxAmount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "minAmount must be less than maxAmount.");
        }

        workerService.startGrab(userId,
            new GrabConfig(req.getMinAmount(), req.getMaxAmount(), req.getTarget()));

        return ResponseEntity.ok().build();
    }

    /**
     * POST /grab/stop
     * Signals the running loop to stop. Returns immediately.
     */
    @PostMapping("/stop")
    public ResponseEntity<Void> stop(@AuthenticationPrincipal String userId) {
        workerService.stopGrab(userId);
        return ResponseEntity.ok().build();
    }

    // ── Status ────────────────────────────────────────────────────────────────

    /**
     * GET /grab/status
     * Returns the current GrabState for this user.
     * Frontend can poll this every 2 s, or use WebSocket instead.
     *
     * Response: { status, grabbed, target, minAmount, maxAmount, running, logs[] }
     */
    @GetMapping("/status")
    public ResponseEntity<GrabState> status(
        @AuthenticationPrincipal String userId
    ) {
        UserSession session = sessionService.getOrThrow(userId);
        return ResponseEntity.ok(session.getGrabState());
    }
}
