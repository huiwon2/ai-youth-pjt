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
    public LocationSearch store(UUID id, String token, String kind, List<SearchChoice> choices, String source) {
        state.lockOwned(id, token);
        OffsetDateTime now = state.now();
        mapper.deleteExpiredChoices(id, now);
        List<LocationItem> items = new ArrayList<>(choices.size());
        for (SearchChoice choice : choices) {
            UUID locationId = UUID.randomUUID();
            mapper.insertChoice(new ChoiceRow(locationId, id, kind,
                    json.writeValueAsString(new StoredChoice(choice, source)), now.plusMinutes(15)));
            items.add(new LocationItem(locationId, choice.name(), choice.roadAddress(), choice.parcelAddress(),
                    choice.longitude(), choice.latitude(), choice.selectable(), choice.reason()));
        }
        return new LocationSearch(List.copyOf(items), source);
    }

    public ChosenLocation resolve(UUID sessionId, UUID id, String kind) {
        ChoiceRow row = mapper.findChoice(sessionId, id);
        if (row == null || !row.kind().equals(kind) || !row.expiresAt().isAfter(state.now())) {
            throw new ApiException(404, "LOCATION_SELECTION_UNAVAILABLE", "선택한 주소를 다시 검색해 주세요.", false);
        }
        StoredChoice stored = json.readValue(row.details(), StoredChoice.class);
        if (!stored.choice().selectable()) {
            throw new ApiException(422, "ADDRESS_NOT_SELECTABLE", "선택할 수 있는 주소를 다시 찾아 주세요.", false);
        }
        return new ChosenLocation(stored.choice().location(), stored.source());
    }

    private record StoredChoice(SearchChoice choice, String source) {}
}
