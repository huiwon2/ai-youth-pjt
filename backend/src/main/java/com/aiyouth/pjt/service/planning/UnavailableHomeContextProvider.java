package com.aiyouth.pjt.service.planning;

import java.util.Arrays;

import org.springframework.stereotype.Component;

import com.aiyouth.pjt.dto.response.common.CommonTypes.Facility;
import com.aiyouth.pjt.dto.response.common.CommonTypes.Location;
import com.aiyouth.pjt.dto.response.common.CommonTypes.Route;
import com.aiyouth.pjt.model.enums.FacilityCategory;
import com.aiyouth.pjt.model.planning.HomeRow;

/**
 * 백엔드 1의 경로·시설 공급원이 연결되기 전의 기본값.
 * 확인하지 못한 값을 추정하지 않고 UNAVAILABLE로 돌려준다 (공급원 장애와 '2km 안에 없음'을 구분).
 */
@Component
public class UnavailableHomeContextProvider implements HomeContextProvider {

    private final PlanningContextReader reader;

    public UnavailableHomeContextProvider(PlanningContextReader reader) {
        this.reader = reader;
    }

    @Override
    public HomeContext lookup(PlanningContext context, HomeRow home) {
        Location location = new Location(home.getLabel(), home.getConfirmedAddress(), null, null);
        return new HomeContext(location, Route.unavailable(reader.now()),
                Arrays.stream(FacilityCategory.values()).map(Facility::unavailable).toList());
    }
}
