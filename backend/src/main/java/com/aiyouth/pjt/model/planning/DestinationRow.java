package com.aiyouth.pjt.model.planning;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** frequent_places 읽기 전용 뷰 (공통 목적지). */
@Getter
@Setter
@NoArgsConstructor
public class DestinationRow {
    private String name;
    private String confirmedAddress;
}
