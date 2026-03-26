package com.incoin.demo.security;

import com.incoin.demo.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Replaces JwtAuthFilter entirely.
 *
 * Reads X-Session-Id header from every request.
 * Looks up the session in Redis.
 * If found → sets authenticated principal = sessionId (used in controllers).
 * If not found → returns 401 (frontend clears state and goes to login screen).
 *
 * No JWT involved at all.
 */
@Component
@RequiredArgsConstructor
public class SessionAuthFilter extends OncePerRequestFilter {

    private final SessionService sessionService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest  request,
            HttpServletResponse response,
            FilterChain         chain
    ) throws ServletException, IOException {

        String sessionId = request.getHeader("X-Session-Id");

        if (sessionId != null && !sessionId.isBlank()) {
            boolean alive = sessionService.find(sessionId).isPresent();
            if (!alive) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Session expired. Please login again.\"}");
                return;
            }
            // Principal = sessionId — controllers use this to load session
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(sessionId, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.contains("/auth/apps")
                || path.contains("/auth/captcha")
                || path.contains("/auth/login");
    }
}