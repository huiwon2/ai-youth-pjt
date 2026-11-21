package com.aiyouth.pjt.global.auth;

import com.aiyouth.pjt.global.exception.ApiErrors;
import com.aiyouth.pjt.global.exception.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
public class ApiRequestFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final Set<String> allowedOrigins;
    private final ObjectMapper json;

    public ApiRequestFilter(@Value("${firsthome.allowed-origins:}") String origins, ObjectMapper json) {
        this.allowedOrigins = Arrays.stream(origins.split(",")).map(String::strip)
                .filter(origin -> !origin.isEmpty()).collect(Collectors.toUnmodifiableSet());
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(ApiErrors.REQUEST_ID, requestId);
        response.setHeader("X-Request-Id", requestId);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (!SAFE_METHODS.contains(request.getMethod()) && !isAllowed(request)) {
            ApiException error = new ApiException(403, "ORIGIN_NOT_ALLOWED", "요청 출처를 확인할 수 없어요.", false);
            response.setStatus(error.status());
            response.setContentType("application/json");
            response.getOutputStream().write(json.writeValueAsBytes(error.body(requestId)));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAllowed(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null) {
            String fetchSite = request.getHeader("Sec-Fetch-Site");
            return fetchSite == null || fetchSite.equals("same-origin") || fetchSite.equals("none");
        }
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean standardPort = scheme.equals("https") ? port == 443 : port == 80;
        String sameOrigin = scheme + "://" + request.getServerName() + (standardPort ? "" : ":" + port);
        return origin.equals(sameOrigin) || allowedOrigins.contains(origin);
    }
}
