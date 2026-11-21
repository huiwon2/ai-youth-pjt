package com.aiyouth.pjt.model.planning;

import java.math.BigDecimal;
import java.util.UUID;

import com.aiyouth.pjt.model.enums.HousingType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** candidate_homes 읽기 전용 뷰. maintenanceWon: null = 미확인, 0 = 없음 확인. */
@Getter
@Setter
@NoArgsConstructor
public class HomeRow {
    private UUID id;
    private int slotNo;
    private String label;
    private String confirmedAddress;
    private HousingType housingType;
    private long depositWon;
    private long monthlyRentWon;
    private Long maintenanceWon;
    private BigDecimal exclusiveAreaM2;
}
