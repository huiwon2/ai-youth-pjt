package com.aiyouth.pjt.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiyouth.pjt.global.exception.ApiException;
import com.aiyouth.pjt.mapper.PlanningMapper;
import com.aiyouth.pjt.model.PlanningRows.ChoiceRow;
import com.aiyouth.pjt.model.PlanningRows.SearchChoice;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;

class LocationChoicesTest {
    @ParameterizedTest
    @CsvSource({
        "HOME, HOME, 0, true, KAKAO",
        "DESTINATION, DESTINATION, -1, true, KAKAO",
        "DESTINATION, HOME, 1, true, KAKAO",
        "HOME, HOME, 1, false, KAKAO",
        "DESTINATION, DESTINATION, 1, true, DEMO"
    })
    void rejectsUnavailableSelections(String requestedKind, String storedKind, int minutesUntilExpiry,
                                      boolean selectable, String source) {
        PlanningMapper mapper = mock(PlanningMapper.class);
        SessionState state = mock(SessionState.class);
        ObjectMapper json = new ObjectMapper();
        LocationChoices choices = new LocationChoices(mapper, state, json);
        OffsetDateTime now = OffsetDateTime.parse("2026-11-01T09:00:00+09:00");
        UUID session = UUID.randomUUID();
        UUID choice = UUID.randomUUID();
        String details = json.writeValueAsString(Map.of("source", source, "choice",
                new SearchChoice("주소", "건물 주소", null, 127.04, 37.50, selectable, null)));
        when(state.now()).thenReturn(now);
        when(mapper.findChoice(session, choice))
                .thenReturn(new ChoiceRow(choice, session, storedKind, details, now.plusMinutes(minutesUntilExpiry)));

        ApiException failure = assertThrows(ApiException.class, () -> choices.resolve(session, choice, requestedKind));
        assertEquals(404, failure.status());
        assertEquals("LOCATION_SELECTION_UNAVAILABLE", failure.body("test").error().code());
    }
}
