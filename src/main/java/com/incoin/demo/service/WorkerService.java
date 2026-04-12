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
 * sessionId  → Redis key (GrabState, session lookup)
 * userId     → DB key (using_service, subscription) = plain username e.g. "vivek"
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkerService {

    private final SessionService        sessionService;
    private final CreditsService        creditsService;
    private final IncoinApiService      incoinApi;
    private final ExecutorService       grabExecutor;
    private final SimpMessagingTemplate wsTemplate;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ConcurrentHashMap<String, Future<?>> activeFutures =
            new ConcurrentHashMap<>();

    public void startGrab(String sessionId, GrabConfig config) {
        stopGrab(sessionId);

        sessionService.updateGrabState(sessionId, state -> {
            state.setRunning(true);
            state.setTarget(config.getTarget());
            state.setMinAmount(config.getMinAmount());
            state.setMaxAmount(config.getMaxAmount());
            state.setGrabbed(0);
            state.setStatus("RUNNING");
            state.getLogs().clear();
        });

        Future<?> future = grabExecutor.submit(() -> runLoop(sessionId));
        activeFutures.put(sessionId, future);
        log.info("[{}] Grab worker started | target={} range=₹{}–₹{}",
                sessionId, config.getTarget(), config.getMinAmount(), config.getMaxAmount());
    }

    public void stopGrab(String sessionId) {
        Future<?> f = activeFutures.remove(sessionId);
        if (f != null && !f.isDone()) {
            f.cancel(true);
            log.info("[{}] Grab worker cancel-signalled", sessionId);
        }
        try {
            sessionService.updateGrabState(sessionId, state -> {
                if ("RUNNING".equals(state.getStatus())) {
                    state.setRunning(false);
                    state.setStatus("STOPPED");
                }
            });
        } catch (Exception ignored) {}
    }

    public boolean isRunning(String sessionId) {
        Future<?> f = activeFutures.get(sessionId);
        return f != null && !f.isDone() && !f.isCancelled();
    }

    private void runLoop(String sessionId) {
        int round = 0;
        try {
            while (true) {
                if (Thread.currentThread().isInterrupted()) break;

                UserSession session = sessionService.getOrThrow(sessionId);
                GrabState   state   = session.getGrabState();

                if (!state.isRunning())                      break;
                if (state.getGrabbed() >= state.getTarget()) break;

                round++;
                appendLog(sessionId, "MUTED", "── Round " + round + " — fetching order pool...");

                List<Map<String, Object>> orders;
                try {
                    orders = incoinApi.getGrabList(session);
                } catch (Exception e) {
                    appendLog(sessionId, "ERROR", "getGrabList failed: " + e.getMessage());
                    log.error("[{}] getGrabList exception", sessionId, e);
                    break;
                }

                appendLog(sessionId, "MUTED", orders.size() + " orders in pool");

                int roundHits = 0;

                for (Map<String, Object> order : orders) {
                    if (Thread.currentThread().isInterrupted()) break;

                    session = sessionService.getOrThrow(sessionId);
                    state   = session.getGrabState();

                    if (!state.isRunning() || state.getGrabbed() >= state.getTarget()) break;

                    String orderId = (String) order.get("orderId");
                    if (orderId == null) continue;

                    double amount = toDouble(order.get("amount"));

                    if (amount < state.getMinAmount() || amount > state.getMaxAmount()) continue;
                    if (!sessionService.addProcessedId(sessionId, orderId)) continue;

                    appendLog(sessionId, "INFO",
                            String.format("Attempting ₹%.2f · %s", amount, orderId));

                    boolean grabbed = false;
                    try {
                        grabbed = incoinApi.grabOrder(session, orderId);
                    } catch (Exception e) {
                        appendLog(sessionId, "ERROR",
                                "grabOrder error [" + orderId + "]: " + e.getMessage());
                        log.warn("[{}] grabOrder exception for {}", sessionId, orderId, e);
                    }

                    if (grabbed) {
                        final double amt = amount;
                        sessionService.updateGrabState(sessionId, s -> {
                            s.setGrabbed(s.getGrabbed() + 1);
                            s.getLogs().add(fmt("SUCCESS",
                                    String.format("✓ Grabbed ₹%.2f · %d/%d",
                                            amt, s.getGrabbed(), s.getTarget())));
                        });

                        // DB save uses userId (plain username) from session
                        try {
                            creditsService.saveGrabbedOrder(session.getUserId(), orderId);
                        } catch (Exception e) {
                            log.warn("[{}] Could not save grabbed order: {}", sessionId, e.getMessage());
                        }

                        roundHits++;
                        pushWs(sessionId);
                    } else {
                        appendLog(sessionId, "WARN", "✗ Grab rejected for orderId=" + orderId);
                    }
                }

                session = sessionService.getOrThrow(sessionId);
                state   = session.getGrabState();

                if (state.getGrabbed() >= state.getTarget()) break;
                if (!state.isRunning())                      break;

                if (roundHits == 0) {
                    appendLog(sessionId, "WARN", "No eligible orders this round — waiting 2 s...");
                }

                Thread.sleep(2_000);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("[{}] Grab loop interrupted", sessionId);

        } catch (Exception e) {
            log.error("[{}] Grab loop fatal error", sessionId, e);
            appendLog(sessionId, "ERROR", "Fatal: " + e.getMessage());

        } finally {
            activeFutures.remove(sessionId);
            finalise(sessionId);
            pushWs(sessionId);
            log.info("[{}] Grab loop ended", sessionId);
        }
    }

    private void finalise(String sessionId) {
        try {
            sessionService.updateGrabState(sessionId, state -> {
                boolean done = state.getGrabbed() >= state.getTarget();
                state.setRunning(false);
                state.setStatus(done ? "DONE" : "STOPPED");
                String msg = done
                        ? "✓ All " + state.getTarget() + " orders grabbed!"
                        : "Stopped — " + state.getGrabbed() + "/" + state.getTarget() + " grabbed";
                state.getLogs().add(fmt(done ? "SUCCESS" : "WARN", msg));
            });
        } catch (Exception e) {
            log.warn("[{}] Could not finalise grab state: {}", sessionId, e.getMessage());
        }
    }

    private void appendLog(String sessionId, String level, String msg) {
        log.debug("[{}][{}] {}", sessionId, level, msg);
        try {
            sessionService.updateGrabState(sessionId, state -> {
                List<String> logs = state.getLogs();
                logs.add(fmt(level, msg));
                if (logs.size() > 500) logs.subList(0, logs.size() - 500).clear();
            });
        } catch (Exception e) {
            log.warn("[{}] Could not append log: {}", sessionId, e.getMessage());
        }
    }

    private String fmt(String level, String msg) {
        return String.format("[%s][%s] %s", LocalTime.now().format(TIME_FMT), level, msg);
    }

    private void pushWs(String sessionId) {
        try {
            UserSession session = sessionService.getOrThrow(sessionId);
            wsTemplate.convertAndSendToUser(
                    sessionId, "/queue/grab-status", session.getGrabState()
            );
        } catch (Exception e) {
            log.debug("[{}] WebSocket push skipped: {}", sessionId, e.getMessage());
        }
    }

    private double toDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(val)); }
        catch (Exception e) { return 0.0; }
    }
}