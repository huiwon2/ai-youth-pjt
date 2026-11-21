package com.aiyouth.pjt.service;

import com.aiyouth.pjt.dto.response.PlanningResponse.*;
import com.aiyouth.pjt.external.kakao.KakaoLocal;
import com.aiyouth.pjt.global.common.RequestFields;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
@Transactional(propagation = Propagation.NEVER)
public class PlanningPlaces {
    private final SessionState state;
    private final LocationChoices choices;
    private final HomeTransactions homes;
    private final HomeLookups lookups;
    private final KakaoLocal kakao;

    public PlanningPlaces(SessionState state, LocationChoices choices, HomeTransactions homes, HomeLookups lookups,
                          KakaoLocal kakao) {
        this.state = state;
        this.choices = choices;
        this.homes = homes;
        this.lookups = lookups;
        this.kakao = kakao;
    }

    public LocationSearch search(UUID id, String token, JsonNode body) {
        choices.checkAccess(id, token);
        RequestFields input = new RequestFields(body, "kind", "query");
        String kind = input.oneOf("kind", "HOME", "DESTINATION");
        String query = input.text("query", 200);
        return choices.store(id, token, kind, kakao.search(kind, query));
    }

    public HomePreview preview(UUID id, String token, JsonNode body) {
        RequestFields input = new RequestFields(body, "inputRevision", "locationId");
        int revision = input.nonNegativeInt("inputRevision");
        var source = homes.previewInput(id, token, revision, input.id("locationId"));
        var lookup = lookups.lookup(source.home(), source.destination(), state.now().toInstant());
        return homes.storePreview(id, token, revision, source, lookup);
    }
}
