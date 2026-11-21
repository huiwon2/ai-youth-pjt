package com.aiyouth.pjt.controller.comparison;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiyouth.pjt.dto.request.comparison.PreferencePatchRequest;
import com.aiyouth.pjt.dto.response.PlanningResponse.Session;
import com.aiyouth.pjt.global.auth.SessionAuth;
import com.aiyouth.pjt.service.comparison.PreferenceService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** API 11 우선순위 저장 → 전체 Session. 저장 후 프론트가 새 revision으로 API 10을 다시 호출한다. */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}")
public class PreferenceController {

    private final SessionAuth auth;
    private final PreferenceService service;

    public PreferenceController(SessionAuth auth, PreferenceService service) {
        this.auth = auth;
        this.service = service;
    }

    @PatchMapping("/preferences")
    public Session update(@PathVariable UUID sessionId,
                          @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                          @Valid @RequestBody PreferencePatchRequest body, HttpServletRequest request) {
        return service.update(sessionId, auth.token(request), ifMatch, body);
    }
}
