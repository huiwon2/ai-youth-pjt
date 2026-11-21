package com.aiyouth.pjt.controller;

import com.aiyouth.pjt.dto.response.PlanningResponse.*;
import com.aiyouth.pjt.global.auth.SessionAuth;
import com.aiyouth.pjt.service.HomeTransactions;
import com.aiyouth.pjt.service.PlanningPlaces;
import com.aiyouth.pjt.service.PlanningSessions;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/sessions")
public class PlanningController {
    private final SessionAuth auth;
    private final PlanningSessions sessions;
    private final PlanningPlaces places;
    private final HomeTransactions homes;

    public PlanningController(SessionAuth auth, PlanningSessions sessions, PlanningPlaces places, HomeTransactions homes) {
        this.auth = auth;
        this.sessions = sessions;
        this.places = places;
        this.homes = homes;
    }

    @PostMapping
    public ResponseEntity<Session> create(@RequestBody JsonNode body) {
        var created = sessions.create(body);
        return ResponseEntity.status(201).header(HttpHeaders.SET_COOKIE, created.cookie().toString()).body(created.session());
    }

    @GetMapping("/{sessionId}")
    public Session get(@PathVariable UUID sessionId, HttpServletRequest request) {
        return sessions.get(sessionId, auth.token(request));
    }

    @PatchMapping("/{sessionId}/profile")
    public Session profile(@PathVariable UUID sessionId, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String version,
                           @RequestBody JsonNode body, HttpServletRequest request) {
        return sessions.patchProfile(sessionId, auth.token(request), version, body);
    }

    @PostMapping("/{sessionId}/location-search")
    public LocationSearch search(@PathVariable UUID sessionId, @RequestBody JsonNode body, HttpServletRequest request) {
        return places.search(sessionId, auth.token(request), body);
    }

    @PostMapping("/{sessionId}/home-previews")
    public HomePreview preview(@PathVariable UUID sessionId, @RequestBody JsonNode body, HttpServletRequest request) {
        return places.preview(sessionId, auth.token(request), body);
    }

    @PutMapping("/{sessionId}/homes/{slot}")
    public Session put(@PathVariable UUID sessionId, @PathVariable int slot,
                       @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String version,
                       @RequestBody JsonNode body, HttpServletRequest request) {
        return homes.put(sessionId, auth.token(request), version, slot, body);
    }

    @DeleteMapping("/{sessionId}/homes/{slot}")
    public Session delete(@PathVariable UUID sessionId, @PathVariable int slot,
                          @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String version,
                          HttpServletRequest request) {
        return homes.delete(sessionId, auth.token(request), version, slot);
    }
}
