package com.aiyouth.pjt.service;

import com.aiyouth.pjt.dto.response.PlanningResponse.*;
import com.aiyouth.pjt.global.exception.ApiException;
import com.aiyouth.pjt.mapper.PlanningMapper;
import com.aiyouth.pjt.model.PlanningRows.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class LocationChoices {
    static final String KAKAO_SOURCE = "KAKAO";

    private final PlanningMapper mapper;
    private final SessionState state;
    private final ObjectMapper json;

    public LocationChoices(PlanningMapper mapper, SessionState state, ObjectMapper json) {
        this.mapper = mapper;
        this.state = state;
        this.json = json;
    }

    @Transactional
    public void checkAccess(UUID id, String token) {
        state.lockOwned(id, token);
    }

    @Transactional
    public LocationSearch store(UUID id, String token, String kind, List<SearchChoice> choices) {
        state.lockOwned(id, token);
        OffsetDateTime now = state.now();
        mapper.deleteExpiredChoices(id, now);
        List<LocationItem> items = new ArrayList<>(choices.size());
        for (SearchChoice choice : choices) {
            UUID locationId = UUID.randomUUID();
            mapper.insertChoice(new ChoiceRow(locationId, id, kind,
                    json.writeValueAsString(new StoredChoice(choice, KAKAO_SOURCE)), now.plusMinutes(15)));
            items.add(new LocationItem(locationId, choice.name(), choice.roadAddress(), choice.parcelAddress(),
                    choice.longitude(), choice.latitude(), choice.selectable(), choice.reason()));
        }
        return new LocationSearch(List.copyOf(items), KAKAO_SOURCE);
    }

    public ChosenLocation resolve(UUID sessionId, UUID id, String kind) {
        ChoiceRow row = mapper.findChoice(sessionId, id);
        if (row == null || !row.kind().equals(kind) || !row.expiresAt().isAfter(state.now())) {
            throw unavailable();
        }
        StoredChoice stored = json.readValue(row.details(), StoredChoice.class);
        if (!stored.choice().selectable() || !KAKAO_SOURCE.equals(stored.source())) {
            throw unavailable();
        }
        return new ChosenLocation(stored.choice().location(), stored.source());
    }

    private static ApiException unavailable() {
        return new ApiException(404, "LOCATION_SELECTION_UNAVAILABLE", "주소를 다시 검색하고 선택해 주세요.", false);
    }

    private record StoredChoice(SearchChoice choice, String source) {}
}
