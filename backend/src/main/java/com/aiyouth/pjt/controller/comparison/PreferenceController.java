package com.aiyouth.pjt.controller.comparison;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiyouth.pjt.dto.request.comparison.PreferencePatchRequest;
import com.aiyouth.pjt.dto.response.comparison.SessionPreferencesResponse;
import com.aiyouth.pjt.global.revision.Revisions;
import com.aiyouth.pjt.service.comparison.PreferenceService;

import jakarta.validation.Valid;

/** API 11 우선순위 저장. 저장 후 프론트가 새 revision으로 API 10을 다시 호출한다. */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}")
public class PreferenceController {

    private final PreferenceService service;

    public PreferenceController(PreferenceService service) {
        this.service = service;
    }

    @PatchMapping("/preferences")
    public ResponseEntity<SessionPreferencesResponse> update(@PathVariable UUID sessionId,
                                                             @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                             @Valid @RequestBody PreferencePatchRequest request) {
        int expectedRevision = Revisions.parseIfMatch(ifMatch);
        SessionPreferencesResponse body = service.update(sessionId, expectedRevision, request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag("\"" + body.revision() + "\"").body(body);
    }
}
