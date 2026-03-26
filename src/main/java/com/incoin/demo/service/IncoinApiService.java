package com.incoin.demo.service;//package com.incoin.demo.service;
//
//import com.incoin.demo.model.CaptchaResult;
//import com.incoin.demo.model.UserSession;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.*;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.HttpClientErrorException;
//import org.springframework.web.client.HttpServerErrorException;
//import org.springframework.web.client.RestTemplate;
//import org.springframework.web.server.ResponseStatusException;
//import org.springframework.web.util.UriComponentsBuilder;
//
//import javax.crypto.Mac;
//import javax.crypto.spec.SecretKeySpec;
//import java.nio.charset.StandardCharsets;
//import java.util.*;
import java.util.List;
//import java.util.stream.Collectors;
//
///**
// * Single class that owns ALL communication with the Incoin third-party API.
// * Sensitive fields (token, clientSecret, signing logic) never leave this class.
// */
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class IncoinApiService {
//
//    private final RestTemplate restTemplate;
//
//    @Value("${app.incoin.base-url}")    private String baseUrl;
//    @Value("${app.incoin.version}")     private String version;
//    @Value("${app.incoin.os-version}")  private String osVersion;
//    @Value("${app.incoin.client-type}") private String clientType;
//    @Value("${app.incoin.app-id}")      private String appId;
//    @Value("${app.incoin.android-id}")  private String androidId;
//
//    // ── checkVersion ──────────────────────────────────────────────────────────
//
//    /**
//     * Fetches clientKey + clientSecret.
//     *
//     * The original JS sent this as a GET with ALL standard headers set
//     * (version, OSVersion, clientType, appId, androidId, timestamp, language,
//     *  gaid, androidId) — but with an EMPTY clientKey ("") since it wasn't
//     * known yet, and NO sign header.
//     *
//     * We replicate that exactly.
//     */
//    @SuppressWarnings("unchecked")
//    public Map<String, String> checkVersion() {
//        String ts  = String.valueOf(System.currentTimeMillis());
//        String url = baseUrl + "/anon/client/checkVersion";
//        log.debug("checkVersion → GET {}", url);
//
//        HttpHeaders h = new HttpHeaders();
//        h.setContentType(MediaType.APPLICATION_JSON);
//        // Send every header the JS sends, with empty clientKey
//        h.set("timestamp",  ts);
//        h.set("version",    version);
//        h.set("OSVersion",  osVersion);
//        h.set("clientKey",  "");           // empty — not yet known
//        h.set("clientType", clientType);
//        h.set("appId",      appId);
//        h.set("language",   "en");
//        h.set("gaid",       "");
//        h.set("androidId",  androidId);
//        // No "token", no "sign" on this call
//
//        try {
//            ResponseEntity<Map> resp = restTemplate.exchange(
//                    url, HttpMethod.GET, new HttpEntity<>(h), Map.class
//            );
//            Map<String, Object> body = resp.getBody();
//            log.debug("checkVersion raw response: {}", body);
//
//            if (body == null || !Boolean.TRUE.equals(body.get("success"))) {
//                String msg = body != null ? String.valueOf(body.get("msg")) : "null body";
//                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
//                        "Incoin checkVersion failed: " + msg);
//            }
//            Map<String, Object> data = (Map<String, Object>) body.get("data");
//            String clientKey    = (String) data.get("clientKey");
//            String clientSecret = (String) data.get("clientSecret");
//            log.debug("checkVersion OK — clientKey={}", clientKey);
//            return Map.of("clientKey", clientKey, "clientSecret", clientSecret);
//
//        } catch (HttpClientErrorException | HttpServerErrorException e) {
//            log.error("checkVersion HTTP error {} — body: {}", e.getStatusCode(), e.getResponseBodyAsString());
//            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
//                    "Incoin checkVersion HTTP error: " + e.getStatusCode());
//        }
//    }
//
//    // ── getCaptcha ────────────────────────────────────────────────────────────
//
//    public CaptchaResult getCaptcha(String clientKey) {
//        String url = baseUrl + "/anon/test/getCaptcha";
//        log.debug("getCaptcha → GET {} (clientKey={})", url, clientKey);
//
//        HttpHeaders h = buildFullHeaders(null, clientKey, "", null);
//
//        try {
//            ResponseEntity<byte[]> resp = restTemplate.exchange(
//                    url, HttpMethod.GET, new HttpEntity<>(h), byte[].class
//            );
//            String captchaToken = resp.getHeaders().getFirst("captchaToken");
//            log.debug("getCaptcha response status={} captchaToken={}", resp.getStatusCode(), captchaToken);
//
//            if (captchaToken == null || captchaToken.isBlank()) {
//                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
//                        "Incoin did not return a captchaToken header");
//            }
//            return new CaptchaResult(captchaToken, resp.getBody());
//
//        } catch (HttpClientErrorException | HttpServerErrorException e) {
//            log.error("getCaptcha HTTP error {} — body: {}", e.getStatusCode(), e.getResponseBodyAsString());
//            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
//                    "Incoin getCaptcha error: " + e.getStatusCode());
//        }
//    }
//
//    // ── login ─────────────────────────────────────────────────────────────────
//
//    @SuppressWarnings("unchecked")
//    public String login(
//            String clientKey, String clientSecret,
//            String username, String password,
//            String captchaCode, String captchaToken
//    ) {
//        Map<String, Object> body = new LinkedHashMap<>();
//        body.put("userName",     username);
//        body.put("passwd",       password);
//        body.put("captcha",      captchaCode);
//        body.put("captchaToken", captchaToken);
//
//        HttpHeaders headers = buildFullHeaders(body, clientKey, clientSecret, null);
//        Map<String, Object> response = post("/anon/login", body, headers);
//        log.debug("login response: success={} msg={}", response.get("success"), response.get("msg"));
//
//        if (Boolean.TRUE.equals(response.get("success"))) {
//            Map<String, Object> data = (Map<String, Object>) response.get("data");
//            String token = (String) data.get("token");
//            if (token == null || token.isBlank()) {
//                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
//                        "Incoin returned an empty token");
//            }
//            log.info("Incoin login OK for user={}", username);
//            return token;
//        }
//        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
//                "Incoin login failed: " + response.get("msg"));
//    }
//
//    // ── getTools ──────────────────────────────────────────────────────────────
//
//    @SuppressWarnings("unchecked")
//    public List<Map<String, Object>> getTools(UserSession session) {
//        HttpHeaders headers = buildFullHeaders(
//                null, session.getClientKey(), session.getClientSecret(), session.getIncoinToken()
//        );
//        ResponseEntity<Map> resp = restTemplate.exchange(
//                baseUrl + "/api/tool/mylist", HttpMethod.GET, new HttpEntity<>(headers), Map.class
//        );
//        Map<String, Object> body = resp.getBody();
//        if (body == null || !Boolean.TRUE.equals(body.get("success"))) return Collections.emptyList();
//        List<Map<String, Object>> all = (List<Map<String, Object>>) body.get("data");
//        if (all == null) return Collections.emptyList();
//        return all.stream()
//                .filter(t -> toInt(t.get("state")) == 20
//                        && toInt(t.get("enableBuy")) == 1
//                        && notBlank(t.get("upiAddr")))
//                .collect(Collectors.toList());
//    }
//
//    // ── getGrabList ───────────────────────────────────────────────────────────
//
//    @SuppressWarnings("unchecked")
//    public List<Map<String, Object>> getGrabList(UserSession session) {
//        Map<String, Object> body = new LinkedHashMap<>();
//        body.put("page", 1);
//        body.put("size", 50);
//        body.put("data", Collections.emptyMap());
//
//        HttpHeaders headers = buildFullHeaders(
//                body, session.getClientKey(), session.getClientSecret(), session.getIncoinToken()
//        );
//        Map<String, Object> response = post("/api/order/grablist", body, headers);
//        if (!Boolean.TRUE.equals(response.get("success"))) return Collections.emptyList();
//        Object data = response.get("data");
//        return (data instanceof List) ? (List<Map<String, Object>>) data : Collections.emptyList();
//    }
//
//    // ── grabOrder ─────────────────────────────────────────────────────────────
//
//    public boolean grabOrder(UserSession session, String orderId) {
//        Map<String, Object> body = new LinkedHashMap<>();
//        body.put("orderId",  orderId);
//        body.put("upiAddr",  session.getSelectedUpiAddr());
//        body.put("toolType", session.getSelectedToolType());
//
//        HttpHeaders headers = buildFullHeaders(
//                body, session.getClientKey(), session.getClientSecret(), session.getIncoinToken()
//        );
//        Map<String, Object> response = post("/api/order/grab", body, headers);
//        boolean ok = Boolean.TRUE.equals(response.get("success"));
//        log.debug("grabOrder id={} ok={} msg={}", orderId, ok, response.get("msg"));
//        return ok;
//    }
//
//    // ── getOrderDetail ────────────────────────────────────────────────────────
//
//    @SuppressWarnings("unchecked")
//    public Map<String, Object> getOrderDetail(UserSession session, String orderId) {
//        Map<String, Object> body = new LinkedHashMap<>();
//        body.put("orderId", orderId);
//        HttpHeaders headers = buildFullHeaders(
//                body, session.getClientKey(), session.getClientSecret(), session.getIncoinToken()
//        );
//        Map<String, Object> response = post("/api/order/detail", body, headers);
//        if (!Boolean.TRUE.equals(response.get("success"))) {
//            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
//                    "Order detail failed: " + response.get("msg"));
//        }
//        return (Map<String, Object>) response.get("data");
//    }
//
//    // ── markPaid ──────────────────────────────────────────────────────────────
//
//    public boolean markPaid(UserSession session, String orderId) {
//        Map<String, Object> body = new LinkedHashMap<>();
//        body.put("orderId", orderId);
//        HttpHeaders headers = buildFullHeaders(
//                body, session.getClientKey(), session.getClientSecret(), session.getIncoinToken()
//        );
//        return Boolean.TRUE.equals(post("/api/order/machine/review", body, headers).get("success"));
//    }
//
//    // ── cancelOrder ───────────────────────────────────────────────────────────
//
//    public boolean cancelOrder(UserSession session, String orderId, String reason) {
//        Map<String, Object> body = new LinkedHashMap<>();
//        body.put("orderId", orderId);
//        body.put("reason",  reason);
//        body.put("remark",  "");
//        HttpHeaders headers = buildFullHeaders(
//                body, session.getClientKey(), session.getClientSecret(), session.getIncoinToken()
//        );
//        return Boolean.TRUE.equals(post("/api/order/cancel", body, headers).get("success"));
//    }
//
//
//    // ── getOrderHistory ───────────────────────────────────────────────────────
//
//    @SuppressWarnings("unchecked")
//    public Map<String, Object> getOrderHistory(UserSession session, int page, int size) {
//        Map<String, Object> body = new LinkedHashMap<>();
//        body.put("page", page);
//        body.put("size", size);
//        body.put("data", Collections.emptyMap());
//        HttpHeaders headers = buildFullHeaders(
//                body, session.getClientKey(), session.getClientSecret(), session.getIncoinToken()
//        );
//        Map<String, Object> response = post("/api/order/list", body, headers);
//        if (!Boolean.TRUE.equals(response.get("success"))) return Collections.emptyMap();
//        return response;
//    }
//
//    // ── logout ────────────────────────────────────────────────────────────────
//
//    public void logout(UserSession session) {
//        try {
//            HttpHeaders headers = buildFullHeaders(
//                    null, session.getClientKey(), session.getClientSecret(), session.getIncoinToken()
//            );
//            restTemplate.exchange(
//                    baseUrl + "/api/user/logout", HttpMethod.GET, new HttpEntity<>(headers), Map.class
//            );
//        } catch (Exception e) {
//            log.warn("Incoin logout failed (ignored): {}", e.getMessage());
//        }
//    }
//
//    // ── Signing ───────────────────────────────────────────────────────────────
//
//    /**
//     * Exact replica of the JS sign() function.
//     * Merges body + device fields, sorts values, joins, then HmacSHA1.
//     */
//    private String sign(
//            Map<String, Object> body,
//            String timestamp,
//            String clientKey,
//            String clientSecret,
//            String token
//    ) {
//        Map<String, Object> m = new LinkedHashMap<>();
//        if (body != null) m.putAll(body);
//        m.put("timestamp",  timestamp);
//        m.put("version",    version);
//        m.put("OSVersion",  osVersion);
//        m.put("clientKey",  clientKey);
//        m.put("clientType", clientType);
//        if (token != null) m.put("token", token);
//
//        String toSign = m.values().stream()
//                .filter(v -> v instanceof String || v instanceof Number)
//                .map(Object::toString)
//                .sorted()
//                .collect(Collectors.joining());
//
//        log.debug("sign input string: [{}]", toSign);
//        return hmacSha1(toSign, clientSecret);
//    }
//
//    private String hmacSha1(String data, String key) {
//        try {
//            Mac mac = Mac.getInstance("HmacSHA1");
//            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
//            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
//            StringBuilder sb = new StringBuilder(hash.length * 2);
//            for (byte b : hash) sb.append(String.format("%02x", b));
//            return sb.toString();
//        } catch (Exception e) {
//            throw new RuntimeException("HmacSHA1 signing failed", e);
//        }
//    }
//
//    /**
//     * Builds the full header set that every authenticated Incoin call requires.
//     * Mirrors the JS getHeaders(body) function exactly:
//     *
//     *   timestamp, version, OSVersion, clientKey, clientType,
//     *   appId, language, gaid, androidId, [token], [sign]
//     *
//     * @param body         request body (null for GET requests) — used for signing only
//     * @param clientKey    Incoin client key
//     * @param clientSecret Incoin client secret (used for HMAC)
//     * @param token        Incoin session token (null for pre-login calls)
//     */
//    private HttpHeaders buildFullHeaders(
//            Map<String, Object> body,
//            String clientKey,
//            String clientSecret,
//            String token
//    ) {
//        String ts = String.valueOf(System.currentTimeMillis());
//        HttpHeaders h = new HttpHeaders();
//        h.setContentType(MediaType.APPLICATION_JSON);
//        h.set("timestamp",  ts);
//        h.set("version",    version);
//        h.set("OSVersion",  osVersion);
//        h.set("clientKey",  clientKey);
//        h.set("clientType", clientType);
//        h.set("appId",      appId);
//        h.set("language",   "en");
//        h.set("gaid",       "");
//        h.set("androidId",  androidId);
//        if (token != null && !token.isBlank()) h.set("token", token);
//        if (body  != null) h.set("sign", sign(body, ts, clientKey, clientSecret, token));
//        return h;
//    }
//
//    // ── HTTP POST helper ──────────────────────────────────────────────────────
//
//    @SuppressWarnings({"rawtypes", "unchecked"})
//    private Map<String, Object> post(String path, Map<String, Object> body, HttpHeaders headers) {
//        log.debug("POST {}{}", baseUrl, path);
//        try {
//            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
//            Map resp = restTemplate.postForObject(baseUrl + path, entity, Map.class);
//            return resp != null ? resp : Collections.emptyMap();
//        } catch (HttpClientErrorException | HttpServerErrorException e) {
//            log.error("POST {} error {} — body: {}", path, e.getStatusCode(), e.getResponseBodyAsString());
//            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
//                    "Incoin API error on " + path + ": " + e.getStatusCode());
//        }
//    }
//
//    // ── Utilities ─────────────────────────────────────────────────────────────
//
//    private int toInt(Object val) {
//        if (val instanceof Number n) return n.intValue();
//        return -1;
//    }
//
//    private boolean notBlank(Object val) {
//        return val instanceof String s && !s.isBlank();
//    }
//}

































//package com.incoin.demo.service;

import com.incoin.demo.model.CaptchaResult;
import com.incoin.demo.model.UserSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Single class that owns ALL communication with the Incoin third-party API.
 * Sensitive fields (token, clientSecret, signing logic) never leave this class.
 *
 * baseUrl is now resolved per-session via resolveBase(session) so each user
 * can point to a different Incoin server selected on Screen 1.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IncoinApiService {

    private final RestTemplate restTemplate;

    @Value("${app.incoin.base-url}")    private String defaultBaseUrl;  // fallback only
    @Value("${app.incoin.version}")     private String version;
    @Value("${app.incoin.os-version}")  private String osVersion;
    @Value("${app.incoin.client-type}") private String clientType;
    @Value("${app.incoin.app-id}")      private String appId;
    @Value("${app.incoin.android-id}")  private String androidId;

    // ── App list (for frontend dropdown) ────────────────────────────────────

    /**
     * Hardcoded list of available Incoin apps.
     * Add more entries here as needed.
     * Index 0, 1, 2... is what the frontend sends as appIndex.
     */
    private static final List<Map<String, Object>> AVAILABLE_APPS = List.of(
            Map.of("name", "InCoin Pay",   "baseUrl", "https://api.incoinpay.net"),
            Map.of("name", "InCoin Pay 2", "baseUrl", "https://api2.incoinpay.net")
            // Add more apps here
    );

    public List<Map<String, Object>> getAvailableApps() {
        return AVAILABLE_APPS;
    }

    public String getBaseUrlByIndex(int index) {
        if (index < 0 || index >= AVAILABLE_APPS.size()) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid app index: " + index
            );
        }
        return (String) AVAILABLE_APPS.get(index).get("baseUrl");
    }

    // ── Base URL resolution ───────────────────────────────────────────────────

    /**
     * Returns the Incoin base URL for a given session.
     * Falls back to the application.properties default if the session has none.
     */
    private String resolveBase(UserSession session) {
        if (session != null) {
            String b = session.getIncoinBaseUrl();
            if (b != null && !b.isBlank()) return b;
        }
        return defaultBaseUrl;
    }

    /**
     * Returns the supplied override, or the application.properties default.
     * Used by pre-session calls (checkVersion, getCaptcha, login).
     */
    private String resolveBase(String overrideBaseUrl) {
        return (overrideBaseUrl != null && !overrideBaseUrl.isBlank())
                ? overrideBaseUrl
                : defaultBaseUrl;
    }

    // ── checkVersion ──────────────────────────────────────────────────────────

    /**
     * Fetches clientKey + clientSecret from the given Incoin server.
     * Called before a session exists, so baseUrl is passed explicitly.
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> checkVersion(String appBaseUrl) {
        String base = resolveBase(appBaseUrl);
        String ts   = String.valueOf(System.currentTimeMillis());
        String url  = base + "/anon/client/checkVersion";
        log.debug("checkVersion → GET {}", url);

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("timestamp",  ts);
        h.set("version",    version);
        h.set("OSVersion",  osVersion);
        h.set("clientKey",  "");
        h.set("clientType", clientType);
        h.set("appId",      appId);
        h.set("language",   "en");
        h.set("gaid",       "");
        h.set("androidId",  androidId);

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(h), Map.class
            );
            Map<String, Object> body = resp.getBody();
            log.debug("checkVersion raw response: {}", body);

            if (body == null || !Boolean.TRUE.equals(body.get("success"))) {
                String msg = body != null ? String.valueOf(body.get("msg")) : "null body";
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Incoin checkVersion failed: " + msg);
            }
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            String clientKey    = (String) data.get("clientKey");
            String clientSecret = (String) data.get("clientSecret");
            log.debug("checkVersion OK — clientKey={}", clientKey);
            return Map.of("clientKey", clientKey, "clientSecret", clientSecret);

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("checkVersion HTTP error {} — body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Incoin checkVersion HTTP error: " + e.getStatusCode());
        }
    }

    /** Backward-compat no-arg overload — uses default base URL. */
    public Map<String, String> checkVersion() {
        return checkVersion(null);
    }

    // ── getCaptcha ────────────────────────────────────────────────────────────

    public CaptchaResult getCaptcha(String clientKey, String appBaseUrl) {
        String base = resolveBase(appBaseUrl);
        String url  = base + "/anon/test/getCaptcha";
        log.debug("getCaptcha → GET {} (clientKey={})", url, clientKey);

        HttpHeaders h = buildFullHeaders(null, clientKey, "", null, base);

        try {
            ResponseEntity<byte[]> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(h), byte[].class
            );
            String captchaToken = resp.getHeaders().getFirst("captchaToken");
            log.debug("getCaptcha response status={} captchaToken={}", resp.getStatusCode(), captchaToken);

            if (captchaToken == null || captchaToken.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Incoin did not return a captchaToken header");
            }
            return new CaptchaResult(captchaToken, resp.getBody());

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("getCaptcha HTTP error {} — body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Incoin getCaptcha error: " + e.getStatusCode());
        }
    }

    /** Backward-compat single-arg overload — uses default base URL. */
    public CaptchaResult getCaptcha(String clientKey) {
        return getCaptcha(clientKey, null);
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public String login(
            String clientKey, String clientSecret,
            String username, String password,
            String captchaCode, String captchaToken,
            String appBaseUrl   // ← explicit, called before session exists
    ) {
        String base = resolveBase(appBaseUrl);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userName",     username);
        body.put("passwd",       password);
        body.put("captcha",      captchaCode);
        body.put("captchaToken", captchaToken);

        HttpHeaders headers = buildFullHeaders(body, clientKey, clientSecret, null, base);
        Map<String, Object> response = post("/anon/login", body, headers, base);
        log.debug("login response: success={} msg={}", response.get("success"), response.get("msg"));

        if (Boolean.TRUE.equals(response.get("success"))) {
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            String token = (String) data.get("token");
            if (token == null || token.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Incoin returned an empty token");
            }
            log.info("Incoin login OK for user={}", username);
            return token;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Incoin login failed: " + response.get("msg"));
    }

    // ── getTools ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTools(UserSession session) {
        String base = resolveBase(session);
        HttpHeaders headers = buildFullHeaders(
                null, session.getClientKey(), session.getClientSecret(), session.getIncoinToken(), base
        );
        ResponseEntity<Map> resp = restTemplate.exchange(
                base + "/api/tool/mylist", HttpMethod.GET, new HttpEntity<>(headers), Map.class
        );
        Map<String, Object> body = resp.getBody();
        if (body == null || !Boolean.TRUE.equals(body.get("success"))) return Collections.emptyList();
        List<Map<String, Object>> all = (List<Map<String, Object>>) body.get("data");
        if (all == null) return Collections.emptyList();
        return all.stream()
                .filter(t -> toInt(t.get("state")) == 20
                        && toInt(t.get("enableBuy")) == 1
                        && notBlank(t.get("upiAddr")))
                .collect(Collectors.toList());
    }

    // ── getGrabList ───────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getGrabList(UserSession session) {
        String base = resolveBase(session);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page", 1);
        body.put("size", 50);
        body.put("data", Collections.emptyMap());

        HttpHeaders headers = buildFullHeaders(
                body, session.getClientKey(), session.getClientSecret(), session.getIncoinToken(), base
        );
        Map<String, Object> response = post("/api/order/grablist", body, headers, base);
        if (!Boolean.TRUE.equals(response.get("success"))) return Collections.emptyList();
        Object data = response.get("data");
        return (data instanceof List) ? (List<Map<String, Object>>) data : Collections.emptyList();
    }

    // ── grabOrder ─────────────────────────────────────────────────────────────

    public boolean grabOrder(UserSession session, String orderId) {
        String base = resolveBase(session);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId",  orderId);
        body.put("upiAddr",  session.getSelectedUpiAddr());
        body.put("toolType", session.getSelectedToolType());

        HttpHeaders headers = buildFullHeaders(
                body, session.getClientKey(), session.getClientSecret(), session.getIncoinToken(), base
        );
        Map<String, Object> response = post("/api/order/grab", body, headers, base);
        boolean ok = Boolean.TRUE.equals(response.get("success"));
        log.debug("grabOrder id={} ok={} msg={}", orderId, ok, response.get("msg"));
        return ok;
    }

    // ── getOrderDetail ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Map<String, Object> getOrderDetail(UserSession session, String orderId) {
        String base = resolveBase(session);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        HttpHeaders headers = buildFullHeaders(
                body, session.getClientKey(), session.getClientSecret(), session.getIncoinToken(), base
        );
        Map<String, Object> response = post("/api/order/detail", body, headers, base);
        if (!Boolean.TRUE.equals(response.get("success"))) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Order detail failed: " + response.get("msg"));
        }
        return (Map<String, Object>) response.get("data");
    }

    // ── markPaid ──────────────────────────────────────────────────────────────

    public boolean markPaid(UserSession session, String orderId) {
        String base = resolveBase(session);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        HttpHeaders headers = buildFullHeaders(
                body, session.getClientKey(), session.getClientSecret(), session.getIncoinToken(), base
        );
        return Boolean.TRUE.equals(post("/api/order/machine/review", body, headers, base).get("success"));
    }

    // ── cancelOrder ───────────────────────────────────────────────────────────

    public boolean cancelOrder(UserSession session, String orderId, String reason) {
        String base = resolveBase(session);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        body.put("reason",  reason);
        body.put("remark",  "");
        HttpHeaders headers = buildFullHeaders(
                body, session.getClientKey(), session.getClientSecret(), session.getIncoinToken(), base
        );
        return Boolean.TRUE.equals(post("/api/order/cancel", body, headers, base).get("success"));
    }

    // ── getOrderHistory ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Map<String, Object> getOrderHistory(UserSession session, int page, int size) {
        String base = resolveBase(session);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page", page);
        body.put("size", size);
        body.put("data", Collections.emptyMap());
        HttpHeaders headers = buildFullHeaders(
                body, session.getClientKey(), session.getClientSecret(), session.getIncoinToken(), base
        );
        Map<String, Object> response = post("/api/order/list", body, headers, base);
        if (!Boolean.TRUE.equals(response.get("success"))) return Collections.emptyMap();
        return response;
    }

    // ── logout ────────────────────────────────────────────────────────────────

    public void logout(UserSession session) {
        String base = resolveBase(session);
        try {
            HttpHeaders headers = buildFullHeaders(
                    null, session.getClientKey(), session.getClientSecret(), session.getIncoinToken(), base
            );
            restTemplate.exchange(
                    base + "/api/user/logout", HttpMethod.GET, new HttpEntity<>(headers), Map.class
            );
        } catch (Exception e) {
            log.warn("Incoin logout failed (ignored): {}", e.getMessage());
        }
    }

    // ── Signing ───────────────────────────────────────────────────────────────

    private String sign(
            Map<String, Object> body,
            String timestamp,
            String clientKey,
            String clientSecret,
            String token
    ) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (body != null) m.putAll(body);
        m.put("timestamp",  timestamp);
        m.put("version",    version);
        m.put("OSVersion",  osVersion);
        m.put("clientKey",  clientKey);
        m.put("clientType", clientType);
        if (token != null) m.put("token", token);

        String toSign = m.values().stream()
                .filter(v -> v instanceof String || v instanceof Number)
                .map(Object::toString)
                .sorted()
                .collect(Collectors.joining());

        log.debug("sign input string: [{}]", toSign);
        return hmacSha1(toSign, clientSecret);
    }

    private String hmacSha1(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("HmacSHA1 signing failed", e);
        }
    }

    /**
     * Builds the full header set required by every Incoin API call.
     * base is passed explicitly so each call uses the session's chosen server.
     */
    private HttpHeaders buildFullHeaders(
            Map<String, Object> body,
            String clientKey,
            String clientSecret,
            String token,
            String base          // ← explicit base URL (not used in headers, but kept for clarity)
    ) {
        String ts = String.valueOf(System.currentTimeMillis());
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("timestamp",  ts);
        h.set("version",    version);
        h.set("OSVersion",  osVersion);
        h.set("clientKey",  clientKey);
        h.set("clientType", clientType);
        h.set("appId",      appId);
        h.set("language",   "en");
        h.set("gaid",       "");
        h.set("androidId",  androidId);
        if (token != null && !token.isBlank()) h.set("token", token);
        if (body  != null) h.set("sign", sign(body, ts, clientKey, clientSecret, token));
        return h;
    }

    // ── HTTP POST helper ──────────────────────────────────────────────────────

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Object> post(
            String path,
            Map<String, Object> body,
            HttpHeaders headers,
            String base          // ← explicit, no longer relies on field
    ) {
        log.debug("POST {}{}", base, path);
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            Map resp = restTemplate.postForObject(base + path, entity, Map.class);
            return resp != null ? resp : Collections.emptyMap();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("POST {} error {} — body: {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Incoin API error on " + path + ": " + e.getStatusCode());
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private int toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        return -1;
    }

    private boolean notBlank(Object val) {
        return val instanceof String s && !s.isBlank();
    }
}