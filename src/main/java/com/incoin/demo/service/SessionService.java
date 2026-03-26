package com.incoin.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incoin.demo.model.GrabState;
import com.incoin.demo.model.UserSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String SESSION_PREFIX = "session:";
    private static final String PENDING_PREFIX = "pending:";

    // 30 minutes session TTL as requested
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final Duration PENDING_TTL = Duration.ofMinutes(5);

    // ── Session CRUD ──────────────────────────────────────────────────────────

    /**
     * Create a new session, store in Redis, return the sessionId.
     * sessionId is a random UUID — used as the auth token sent to frontend.
     */
    public String createSession(UserSession session) {
        String sessionId = UUID.randomUUID().toString();
        session.setLastActiveAt(Instant.now());
        redisTemplate.opsForValue().set(SESSION_PREFIX + sessionId, session, SESSION_TTL);
        log.debug("Session created sessionId={} userId={}", sessionId, session.getUserId());
        return sessionId;
    }

    public void save(UserSession session, String sessionId) {
        session.setLastActiveAt(Instant.now());
        redisTemplate.opsForValue().set(SESSION_PREFIX + sessionId, session, SESSION_TTL);
    }

    public Optional<UserSession> find(String sessionId) {
        Object raw = redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId);
        if (raw == null) return Optional.empty();
        UserSession session = toUserSession(raw);
        // Slide TTL on each access
        redisTemplate.expire(SESSION_PREFIX + sessionId, SESSION_TTL);
        return Optional.of(session);
    }

    public UserSession getOrThrow(String sessionId) {
        return find(sessionId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Session expired or not found. Please login again.")
        );
    }

    public void delete(String sessionId) {
        redisTemplate.delete(SESSION_PREFIX + sessionId);
        log.info("Session deleted sessionId={}", sessionId);
    }

    // ── GrabState helpers ─────────────────────────────────────────────────────

    public void updateGrabState(String sessionId, Consumer<GrabState> updater) {
        UserSession session = getOrThrow(sessionId);
        updater.accept(session.getGrabState());
        save(session, sessionId);
    }

    public boolean addProcessedId(String sessionId, String orderId) {
        UserSession session = getOrThrow(sessionId);
        boolean added = session.getProcessedIds().add(orderId);
        if (added) save(session, sessionId);
        return added;
    }

    // ── Pending captcha keys ──────────────────────────────────────────────────

    public void storePendingKeys(String captchaToken, Map<String, String> keys) {
        redisTemplate.opsForValue().set(
                PENDING_PREFIX + captchaToken,
                new LinkedHashMap<>(keys),
                PENDING_TTL
        );
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getPendingKeys(String captchaToken) {
        String key = PENDING_PREFIX + captchaToken;
        Object raw = redisTemplate.opsForValue().get(key);
        if (raw == null) return null;
        redisTemplate.delete(key);
        return toStringMap(raw);
    }

    // ── Type helpers ──────────────────────────────────────────────────────────

    private UserSession toUserSession(Object raw) {
        if (raw instanceof UserSession s) return s;
        return objectMapper.convertValue(raw, UserSession.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> toStringMap(Object raw) {
        if (raw instanceof Map<?, ?> m) {
            Map<String, String> result = new LinkedHashMap<>();
            m.forEach((k, v) -> { if (k != null && v != null) result.put(k.toString(), v.toString()); });
            return result;
        }
        return objectMapper.convertValue(raw, Map.class);
    }
}