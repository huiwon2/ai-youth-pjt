package com.aiyouth.pjt.service.planning;

import java.util.List;

import com.aiyouth.pjt.dto.response.common.CommonTypes.Facility;
import com.aiyouth.pjt.dto.response.common.CommonTypes.Location;
import com.aiyouth.pjt.dto.response.common.CommonTypes.Route;
import com.aiyouth.pjt.model.planning.HomeRow;

/**
 * 후보 집의 위치·경로·주변 시설 공급원.
 * 카카오·ODsay 조회와 미리보기(previewId) 캐시는 백엔드 1 담당이다. 백엔드 1이 이 인터페이스를
 * 구현한 빈을 @Primary로 등록하면 비교 계산이 그 값을 사용한다.
 */
public interface HomeContextProvider {

    HomeContext lookup(PlanningContext context, HomeRow home);

    record HomeContext(Location location, Route route, List<Facility> facilities) {
    }
}
