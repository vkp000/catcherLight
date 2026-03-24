//package com.incoin.demo.security;
//
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import lombok.RequiredArgsConstructor;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.stereotype.Component;
//import org.springframework.web.filter.OncePerRequestFilter;
//
//import java.io.IOException;
//import java.util.List;
//
///**
// * Reads the Bearer token from the Authorization header,
// * validates it, and injects the userId as the authenticated principal.
// * Controllers can then obtain the userId via @AuthenticationPrincipal String userId.
// */
//@Component
//@RequiredArgsConstructor
//public class JwtAuthFilter extends OncePerRequestFilter {
//
//    private final JwtUtil jwtUtil;
//
//    @Override
//    protected void doFilterInternal(
//        HttpServletRequest  request,
//        HttpServletResponse response,
//        FilterChain         chain
//    ) throws ServletException, IOException {
//
//        String authHeader = request.getHeader("Authorization");
//
//        if (authHeader != null && authHeader.startsWith("Bearer ")) {
//            String token = authHeader.substring(7);
//            if (jwtUtil.isValid(token)) {
//                String userId = jwtUtil.extractUserId(token);
//                UsernamePasswordAuthenticationToken auth =
//                    new UsernamePasswordAuthenticationToken(userId, null, List.of());
//                SecurityContextHolder.getContext().setAuthentication(auth);
//            }
//        }
//
//        chain.doFilter(request, response);
//    }
//}























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
 * Reads the Bearer token from the Authorization header, validates it,
 * and injects the userId as the authenticated principal.
 *
 * Also checks that the Incoin session (stored in Redis) still exists.
 * If the Redis session is gone — either because the Incoin token expired
 * or the TTL lapsed — the request is rejected with 401 so the frontend
 * clears state and redirects to Screen 1 (login).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil        jwtUtil;
    private final SessionService sessionService;   // ← NEW: needed for session check

    @Override
    protected void doFilterInternal(
            HttpServletRequest  request,
            HttpServletResponse response,
            FilterChain         chain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (jwtUtil.isValid(token)) {
                String userId = jwtUtil.extractUserId(token);

                // ── Incoin session check ───────────────────────────────────
                // Even if our JWT is valid, the Incoin session in Redis may have
                // expired (Redis TTL or Incoin token timeout).
                // If the session is gone, reject with 401 — the frontend's api()
                // helper already calls doLogout() on any 401 response.
                boolean sessionAlive = sessionService.find(userId).isPresent();
                if (!sessionAlive) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"message\":\"Session expired. Please login again.\"}");
                    return; // stop filter chain — do NOT call chain.doFilter
                }

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Skip the session check for public (pre-login) endpoints.
     * Without this, /auth/captcha and /auth/login would be blocked because
     * no session exists yet when those calls are made.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.contains("/auth/captcha")
                || path.contains("/auth/login");
    }
}