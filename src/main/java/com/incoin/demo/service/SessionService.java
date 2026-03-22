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
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String SESSION_PREFIX = "session:";
    private static final String PENDING_PREFIX = "pending:";

    private static final Duration SESSION_TTL = Duration.ofHours(2);
    private static final Duration PENDING_TTL  = Duration.ofMinutes(5);

    // ── User Session ──────────────────────────────────────────────────────────

    public void save(UserSession session) {
        session.setLastActiveAt(Instant.now());
        redisTemplate.opsForValue().set(
                SESSION_PREFIX + session.getUserId(), session, SESSION_TTL
        );
        log.debug("Session saved userId={}", session.getUserId());
    }

    public Optional<UserSession> find(String userId) {
        Object raw = redisTemplate.opsForValue().get(SESSION_PREFIX + userId);
        if (raw == null) return Optional.empty();
        UserSession session = toUserSession(raw);
        redisTemplate.expire(SESSION_PREFIX + userId, SESSION_TTL);
        return Optional.of(session);
    }

    public UserSession getOrThrow(String userId) {
        return find(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Session expired or not found. Please login again.")
        );
    }

    public void delete(String userId) {
        redisTemplate.delete(SESSION_PREFIX + userId);
        log.info("Session deleted userId={}", userId);
    }

    // ── GrabState helpers ─────────────────────────────────────────────────────

    public void updateGrabState(String userId, Consumer<GrabState> updater) {
        UserSession session = getOrThrow(userId);
        updater.accept(session.getGrabState());
        save(session);
    }

    public boolean addProcessedId(String userId, String orderId) {
        UserSession session = getOrThrow(userId);
        boolean added = session.getProcessedIds().add(orderId);
        if (added) save(session);
        return added;
    }

    // ── Pending captcha keys ──────────────────────────────────────────────────

    public void storePendingKeys(String captchaToken, Map<String, String> keys) {
        // Store as plain LinkedHashMap — GenericJackson2JsonRedisSerializer
        // will embed @class info automatically.
        redisTemplate.opsForValue().set(
                PENDING_PREFIX + captchaToken,
                new LinkedHashMap<>(keys),
                PENDING_TTL
        );
        log.debug("Stored pending keys for captchaToken={}", captchaToken);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getPendingKeys(String captchaToken) {
        String key = PENDING_PREFIX + captchaToken;
        Object raw = redisTemplate.opsForValue().get(key);
        if (raw == null) {
            log.warn("No pending keys found for captchaToken={}", captchaToken);
            return null;
        }
        redisTemplate.delete(key); // one-time use
        // Convert whatever Jackson deserialized back to Map<String,String>
        return toStringMap(raw);
    }

    // ── Type-safe conversion helpers ──────────────────────────────────────────

    private UserSession toUserSession(Object raw) {
        if (raw instanceof UserSession s) return s;
        // Jackson may deserialize as LinkedHashMap when type info is missing
        return objectMapper.convertValue(raw, UserSession.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> toStringMap(Object raw) {
        if (raw instanceof Map<?, ?> m) {
            Map<String, String> result = new LinkedHashMap<>();
            m.forEach((k, v) -> {
                if (k != null && v != null) result.put(k.toString(), v.toString());
            });
            return result;
        }
        return objectMapper.convertValue(raw, Map.class);
    }
}