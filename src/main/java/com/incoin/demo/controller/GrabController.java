package com.incoin.demo.controller;

import com.incoin.demo.dto.SelectToolRequest;
import com.incoin.demo.dto.StartGrabRequest;
import com.incoin.demo.model.GrabConfig;
import com.incoin.demo.model.GrabState;
import com.incoin.demo.model.UserSession;
import com.incoin.demo.service.IncoinApiService;
import com.incoin.demo.service.SessionService;
import com.incoin.demo.service.SubscriptionService;
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

@RestController
@RequestMapping("/grab")
@RequiredArgsConstructor
public class GrabController {

    private final SessionService      sessionService;
    private final IncoinApiService    incoinApi;
    private final WorkerService       workerService;
    private final SubscriptionService subscriptionService;

    @GetMapping("/tools")
    public ResponseEntity<List<Map<String, Object>>> getTools(
            @AuthenticationPrincipal String sessionId
    ) {
        UserSession session = sessionService.getOrThrow(sessionId);
        return ResponseEntity.ok(incoinApi.getTools(session));
    }

    @PostMapping("/tool")
    public ResponseEntity<Void> selectTool(
            @AuthenticationPrincipal String sessionId,
            @Valid @RequestBody SelectToolRequest req
    ) {
        UserSession session = sessionService.getOrThrow(sessionId);
        session.setSelectedUpiAddr(req.getUpiAddr());
        session.setSelectedToolType(req.getToolType());
        session.setSelectedToolName(req.getToolName());
        sessionService.save(session, sessionId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/start")
    public ResponseEntity<Void> start(
            @AuthenticationPrincipal String sessionId,
            @Valid @RequestBody StartGrabRequest req
    ) {
        UserSession session = sessionService.getOrThrow(sessionId);

        if (session.getSelectedUpiAddr() == null || session.getSelectedUpiAddr().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No tool selected. Call POST /grab/tool first.");
        }
        if (req.getMinAmount() >= req.getMaxAmount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "minAmount must be less than maxAmount.");
        }

        int credits = subscriptionService.getCredits(session.getUserId());
        if (credits <= 0) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "No credits remaining. Please redeem a coupon.");
        }

        // sessionId passed — WorkerService uses it as Redis key
        workerService.startGrab(sessionId,
                new GrabConfig(req.getMinAmount(), req.getMaxAmount(), req.getTarget()));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stop")
    public ResponseEntity<Void> stop(@AuthenticationPrincipal String sessionId) {
        workerService.stopGrab(sessionId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/status")
    public ResponseEntity<GrabState> status(@AuthenticationPrincipal String sessionId) {
        UserSession session = sessionService.getOrThrow(sessionId);
        return ResponseEntity.ok(session.getGrabState());
    }
}