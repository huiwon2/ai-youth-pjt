package com.aiyouth.pjt.global.auth;

import com.aiyouth.pjt.global.exception.ApiException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class SessionAuth {
    private static final String COOKIE_NAME = "firsthome_session";
    private final SecureRandom random = new SecureRandom();
    private final boolean secure;

    public SessionAuth(@Value("${firsthome.session.cookie-secure:true}") boolean secure) {
        this.secure = secure;
    }

    public String issue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public ResponseCookie cookie(UUID sessionId, String token, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, token)
                .path("/api/v1/sessions/" + sessionId)
                .httpOnly(true).secure(secure).sameSite("Lax").maxAge(maxAge).build();
    }

    public String token(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        throw expired();
    }

    public String hash(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw expired();
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public ApiException expired() {
        return new ApiException(401, "SESSION_EXPIRED", "작업 세션이 만료됐거나 쿠키를 확인할 수 없어요.", false);
    }
}
