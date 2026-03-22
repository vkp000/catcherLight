package com.incoin.demo.service;

import com.incoin.demo.model.GrabConfig;
import com.incoin.demo.model.GrabState;
import com.incoin.demo.model.UserSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Manages one background grab loop per user.
 * Each user gets an isolated Future in a ConcurrentHashMap.
 * All state is stored in Redis (via SessionService) — never in instance fields.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkerService {

    private final SessionService    sessionService;
    private final IncoinApiService  incoinApi;
    private final ExecutorService   grabExecutor;
    private final SimpMessagingTemplate wsTemplate;

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    /** One Future per userId. */
    private final ConcurrentHashMap<String, Future<?>> activeFutures =
        new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Start a grab loop for the given user.
     * If a loop is already running for this user it is cancelled first.
     */
    public void startGrab(String userId, GrabConfig config) {
        stopGrab(userId); // cancel any existing worker

        // Initialise GrabState in Redis before the thread starts
        sessionService.updateGrabState(userId, state -> {
            state.setRunning(true);
            state.setTarget(config.getTarget());
            state.setMinAmount(config.getMinAmount());
            state.setMaxAmount(config.getMaxAmount());
            state.setGrabbed(0);
            state.setStatus("RUNNING");
            state.getLogs().clear();
        });

        Future<?> future = grabExecutor.submit(() -> runLoop(userId));
        activeFutures.put(userId, future);
        log.info("[{}] Grab worker started | target={} range=₹{}–₹{}",
            userId, config.getTarget(), config.getMinAmount(), config.getMaxAmount());
    }

    /**
     * Signal a running loop to stop.
     * The loop checks the running flag after every sleep, so it stops within ~2 s.
     */
    public void stopGrab(String userId) {
        Future<?> f = activeFutures.remove(userId);
        if (f != null && !f.isDone()) {
            f.cancel(true);
            log.info("[{}] Grab worker cancel-signalled", userId);
        }
        // Update Redis state only if currently RUNNING (don't clobber DONE/STOPPED)
        try {
            sessionService.updateGrabState(userId, state -> {
                if ("RUNNING".equals(state.getStatus())) {
                    state.setRunning(false);
                    state.setStatus("STOPPED");
                }
            });
        } catch (Exception ignored) {
            // Session may already be gone (logout)
        }
    }

    /** Returns true if a non-done Future exists for this user. */
    public boolean isRunning(String userId) {
        Future<?> f = activeFutures.get(userId);
        return f != null && !f.isDone() && !f.isCancelled();
    }

    // ── Core Loop ─────────────────────────────────────────────────────────────

    private void runLoop(String userId) {
        int round = 0;
        try {
            while (true) {
                if (Thread.currentThread().isInterrupted()) break;

                // ── Reload session from Redis every iteration ──────────────
                // This is intentional: the stop signal sets running=false in Redis,
                // and we pick it up here without needing shared in-memory flags.
                UserSession session = sessionService.getOrThrow(userId);
                GrabState   state   = session.getGrabState();

                if (!state.isRunning())                    break;
                if (state.getGrabbed() >= state.getTarget()) break;

                round++;
                appendLog(userId, "MUTED",
                    "── Round " + round + " — fetching order pool...");

                // ── Fetch order list ───────────────────────────────────────
                List<Map<String, Object>> orders;
                try {
                    orders = incoinApi.getGrabList(session);
                } catch (Exception e) {
                    appendLog(userId, "ERROR", "getGrabList failed: " + e.getMessage());
                    log.error("[{}] getGrabList exception", userId, e);
                    break;
                }

                appendLog(userId, "MUTED", orders.size() + " orders in pool");

                // ── Process each candidate order ───────────────────────────
                int roundHits = 0;

                for (Map<String, Object> order : orders) {
                    if (Thread.currentThread().isInterrupted()) break;

                    // Reload per-order: stop can arrive at any moment
                    session = sessionService.getOrThrow(userId);
                    state   = session.getGrabState();

                    if (!state.isRunning() || state.getGrabbed() >= state.getTarget()) break;

                    String orderId = (String) order.get("orderId");
                    if (orderId == null) continue;

                    double amount = toDouble(order.get("amount"));

                    // ── Filters ────────────────────────────────────────────
                    if (amount < state.getMinAmount() || amount > state.getMaxAmount()) continue;

                    // addProcessedId is atomic: returns false if already added
                    if (!sessionService.addProcessedId(userId, orderId)) continue;

                    appendLog(userId, "INFO",
                        String.format("Attempting ₹%.2f · %s", amount, orderId));

                    // ── Grab attempt ───────────────────────────────────────
                    boolean grabbed = false;
                    try {
                        grabbed = incoinApi.grabOrder(session, orderId);
                    } catch (Exception e) {
                        appendLog(userId, "ERROR",
                            "grabOrder error [" + orderId + "]: " + e.getMessage());
                        log.warn("[{}] grabOrder exception for {}", userId, orderId, e);
                    }

                    if (grabbed) {
                        final double amt = amount;
                        sessionService.updateGrabState(userId, s -> {
                            s.setGrabbed(s.getGrabbed() + 1);
                            s.getLogs().add(fmt("SUCCESS",
                                String.format("✓ Grabbed ₹%.2f · %d/%d",
                                    amt, s.getGrabbed(), s.getTarget())));
                        });
                        roundHits++;
                        pushWs(userId);
                    } else {
                        appendLog(userId, "WARN",
                            "✗ Grab rejected for orderId=" + orderId);
                    }
                } // end for-each order

                // ── Post-round checks ──────────────────────────────────────
                session = sessionService.getOrThrow(userId);
                state   = session.getGrabState();

                if (state.getGrabbed() >= state.getTarget()) break;
                if (!state.isRunning())                      break;

                if (roundHits == 0) {
                    appendLog(userId, "WARN", "No eligible orders this round — waiting 2 s...");
                }

                Thread.sleep(2_000);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("[{}] Grab loop interrupted", userId);

        } catch (Exception e) {
            log.error("[{}] Grab loop fatal error", userId, e);
            appendLog(userId, "ERROR", "Fatal: " + e.getMessage());

        } finally {
            activeFutures.remove(userId);
            finalise(userId);
            pushWs(userId);
            log.info("[{}] Grab loop ended", userId);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void finalise(String userId) {
        try {
            sessionService.updateGrabState(userId, state -> {
                boolean done = state.getGrabbed() >= state.getTarget();
                state.setRunning(false);
                state.setStatus(done ? "DONE" : "STOPPED");
                String msg = done
                    ? "✓ All " + state.getTarget() + " orders grabbed!"
                    : "Stopped — " + state.getGrabbed() + "/" + state.getTarget() + " grabbed";
                state.getLogs().add(fmt(done ? "SUCCESS" : "WARN", msg));
            });
        } catch (Exception e) {
            log.warn("[{}] Could not finalise grab state: {}", userId, e.getMessage());
        }
    }

    private void appendLog(String userId, String level, String msg) {
        log.debug("[{}][{}] {}", userId, level, msg);
        try {
            sessionService.updateGrabState(userId, state -> {
                List<String> logs = state.getLogs();
                logs.add(fmt(level, msg));
                // Rolling window: keep last 500 lines only
                if (logs.size() > 500) {
                    logs.subList(0, logs.size() - 500).clear();
                }
            });
        } catch (Exception e) {
            log.warn("[{}] Could not append log: {}", userId, e.getMessage());
        }
    }

    private String fmt(String level, String msg) {
        return String.format("[%s][%s] %s",
            LocalTime.now().format(TIME_FMT), level, msg);
    }

    private void pushWs(String userId) {
        try {
            UserSession session = sessionService.getOrThrow(userId);
            wsTemplate.convertAndSendToUser(
                userId, "/queue/grab-status", session.getGrabState()
            );
        } catch (Exception e) {
            log.debug("[{}] WebSocket push skipped: {}", userId, e.getMessage());
        }
    }

    private double toDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(val)); }
        catch (Exception e) { return 0.0; }
    }
}
