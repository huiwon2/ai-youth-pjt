"""추천 알고리즘 정확도 검증.

정답은 알고리즘과 독립적으로 정한다.
- 명백: 상식으로 답이 하나인 사례(지배 관계, 극단적 맞교환, 제약 조건). 정답을 손으로 적었다.
- 판단: 사람마다 갈릴 수 있는 맞교환. 프리셋별 가상 사용자의 '월 환산 손익'으로 정답을 정한다.
  가상 사용자 모델은 알고리즘 점수식과 다르게 실제 이용 횟수 기반으로 계산한다.

실행: python -B evaluate_accuracy.py
"""

from dataclasses import dataclass, replace

from recommendation import House, recommend

# 가상 사용자: (통근·도보 시간의 시간당 가치 원, 생활 시설 중요도 배수)
PERSONAS = {
    "BALANCED": (10_000, 1.0),      # 2026 최저임금 수준
    "COST": (5_000, 1.0),           # 돈이 우선, 시간 가치 절반
    "COMMUTE": (20_000, 1.0),       # 시간이 우선, 시간 가치 2배
    "NEIGHBORHOOD": (8_000, 2.0),   # 동네 생활을 두 배로 중시
}
# 월 왕복 이용 횟수: 마트 주 2회, 편의점 주 3회, 약국 월 2회
TRIPS = {"supermarket_walk_minutes": 8, "convenience_store_walk_minutes": 12, "pharmacy_walk_minutes": 2}
COMMUTE_DAYS = 20
DEPOSIT_RATE = 0.045


def h(id, rent, fee, commute, mart=8, store=8, pharmacy=8, deposit=10_000_000, support=0):
    return House(
        id, rent, fee, commute, mart,
        deposit_won=deposit,
        convenience_store_walk_minutes=store,
        pharmacy_walk_minutes=pharmacy,
        monthly_support_won=support,
    )


def persona_monthly_burden(house: House, preset: str, rate: float = DEPOSIT_RATE) -> float:
    """가상 사용자가 느끼는 월 부담(원). 낮을수록 좋다."""
    vot, convenience_multiplier = PERSONAS[preset]
    money = max(0, house.rent_won + house.maintenance_won - house.monthly_support_won)
    money += house.deposit_won * rate / 12
    commute_hours = house.commute_minutes * 2 * COMMUTE_DAYS / 60
    walk_hours = sum(trips * 2 * (getattr(house, name) or 0) / 60 for name, trips in TRIPS.items())
    return money + vot * commute_hours + vot * convenience_multiplier * walk_hours


@dataclass
class Case:
    id: str
    category: str
    kind: str          # 명백 | 판단
    description: str
    houses: list
    priority: str
    expected: tuple    # 기대 순위(1위만 비교할 땐 길이 1)
    options: dict = None
    weights: tuple = None


A, B = "A", "B"
CASES = [
    # A. 지배 관계: 한 집이 모든 면에서 같거나 낫다.
    Case("A1", "지배", "명백", "A가 5만 원 싸고 10분 빠름", [h(A, 500_000, 50_000, 30), h(B, 550_000, 50_000, 40)], "BALANCED", (A,)),
    Case("A2", "지배", "명백", "비용 같고 A가 5분 빠름 (COST)", [h(A, 500_000, 50_000, 35), h(B, 500_000, 50_000, 40)], "COST", (A,)),
    Case("A3", "지배", "명백", "통근 같고 A가 10만 원 쌈 (COMMUTE)", [h(A, 500_000, 50_000, 30), h(B, 600_000, 50_000, 30)], "COMMUTE", (A,)),
    Case("A4", "지배", "명백", "비용·통근 같고 A가 시설 가까움 (NEIGHBORHOOD)",
         [h(A, 500_000, 50_000, 30, 5, 5, 5), h(B, 500_000, 50_000, 30, 12, 12, 12)], "NEIGHBORHOOD", (A,)),
    Case("A5", "지배", "명백", "3곳 연쇄 지배 → A>B>C",
         [h("C", 600_000, 60_000, 50, 15, 15, 15), h(A, 450_000, 50_000, 25, 5, 5, 5), h(B, 520_000, 55_000, 35, 9, 9, 9)],
         "BALANCED", (A, B, "C")),
    Case("A6", "지배", "명백", "모두 같고 A 보증금만 낮음(500만 vs 2,000만)",
         [h(A, 500_000, 50_000, 30, deposit=5_000_000), h(B, 500_000, 50_000, 30, deposit=20_000_000)], "BALANCED", (A,)),

    # B. 극단적 비용-통근 맞교환
    Case("B1", "비용·통근", "명백", "B가 1만 원 더 내고 통근 50→20분", [h(A, 500_000, 50_000, 50), h(B, 510_000, 50_000, 20)], "BALANCED", (B,)),
    Case("B2", "비용·통근", "명백", "B가 30만 원 더 내고 통근 3분 단축", [h(A, 500_000, 50_000, 30), h(B, 800_000, 50_000, 27)], "BALANCED", (A,)),
    Case("B3", "비용·통근", "명백", "COST여도 3만 원에 편도 35분 단축이면 B", [h(A, 450_000, 50_000, 60), h(B, 480_000, 50_000, 25)], "COST", (B,)),
    Case("B4", "비용·통근", "명백", "COMMUTE여도 40만 원에 5분 단축은 A", [h(A, 450_000, 50_000, 35), h(B, 850_000, 50_000, 30)], "COMMUTE", (A,)),
    Case("B5", "비용·통근", "명백", "B가 2만 원 더 내고 통근 70→40분", [h(A, 480_000, 50_000, 70), h(B, 500_000, 50_000, 40)], "BALANCED", (B,)),
    Case("B6", "비용·통근", "명백", "COST에서 15만 원 더 내고 10분 단축은 A", [h(A, 450_000, 50_000, 30), h(B, 600_000, 50_000, 20)], "COST", (A,)),

    # C. 보증금 (전월세전환율 4.5% 기준 상식 계산)
    Case("C1", "보증금", "명백", "보증금 1억/월세 30만 vs 500만/60만 → 환산 시 B가 쌈",
         [h(A, 300_000, 50_000, 30, deposit=100_000_000), h(B, 600_000, 50_000, 30, deposit=5_000_000)], "BALANCED", (B,)),
    Case("C2", "보증금", "명백", "보증금 5,000만/40만 vs 1,000만/60만 → 환산 시 A가 쌈",
         [h(A, 400_000, 50_000, 30, deposit=50_000_000), h(B, 600_000, 50_000, 30, deposit=10_000_000)], "BALANCED", (A,)),
    Case("C3", "보증금", "명백", "보증금 2억/관리비만 vs 1,000만/70만 (COST) → 환산 시 B",
         [h(A, 0, 100_000, 30, deposit=200_000_000), h(B, 700_000, 50_000, 30, deposit=10_000_000)], "COST", (B,)),
    Case("C4", "보증금", "명백", "C1과 같지만 환산율 2% → A가 쌈",
         [h(A, 300_000, 50_000, 30, deposit=100_000_000), h(B, 600_000, 50_000, 30, deposit=5_000_000)], "BALANCED", (A,),
         options={"deposit_annual_rate": 0.02}),

    # D. 월 지원금
    Case("D1", "지원금", "명백", "B 월세 15만 원 비싸지만 월 20만 원 지원",
         [h(A, 500_000, 50_000, 30), h(B, 650_000, 50_000, 30, support=200_000)], "BALANCED", (B,)),
    Case("D2", "지원금", "명백", "예산 60만: A는 지원 후 55만이라 통과, B는 67만이라 제외",
         [h(A, 650_000, 50_000, 30, support=150_000), h(B, 620_000, 50_000, 25)], "COST", (A,),
         options={"max_monthly_cost_won": 600_000}),

    # E. 생활 편의
    Case("E1", "생활 편의", "명백", "비용·통근 같고 A 시설 3분 vs B 20분",
         [h(A, 500_000, 50_000, 30, 3, 3, 3), h(B, 500_000, 50_000, 30, 20, 20, 20)], "BALANCED", (A,)),
    Case("E2", "생활 편의", "판단", "A가 10만 원 쌈, 대신 시설이 10분씩 멂 (BALANCED)",
         [h(A, 400_000, 50_000, 30, 15, 15, 15), h(B, 500_000, 50_000, 30, 5, 5, 5)], "BALANCED", None),
    Case("E3", "생활 편의", "판단", "E2와 같은 집, NEIGHBORHOOD",
         [h(A, 400_000, 50_000, 30, 15, 15, 15), h(B, 500_000, 50_000, 30, 5, 5, 5)], "NEIGHBORHOOD", None),
    Case("E4", "생활 편의", "판단", "A는 마트 25분·편의점 5분, B는 모두 10분",
         [h(A, 500_000, 50_000, 30, 25, 5, 10), h(B, 500_000, 50_000, 30, 10, 10, 10)], "BALANCED", None),
    Case("E5", "생활 편의", "판단", "A가 5만 원 쌈, 시설이 4분씩 멂 (BALANCED)",
         [h(A, 450_000, 50_000, 30, 12, 12, 12), h(B, 500_000, 50_000, 30, 8, 8, 8)], "BALANCED", None),
    Case("E6", "생활 편의", "판단", "A가 3만 원 쌈, 편의점만 15분 멂 (BALANCED)",
         [h(A, 470_000, 50_000, 30, 8, 20, 8), h(B, 500_000, 50_000, 30, 8, 5, 8)], "BALANCED", None),

    # F. 프리셋별 중간 맞교환: B가 12만 원 더 내고 편도 20분 단축
    *[
        Case(f"F{i}", "프리셋", "판단", f"12만 원 더 내고 편도 20분 단축 ({preset})",
             [h(A, 500_000, 50_000, 45), h(B, 620_000, 50_000, 25)], preset, None)
        for i, preset in enumerate(("BALANCED", "COST", "COMMUTE", "NEIGHBORHOOD"), start=1)
    ],
    Case("F5", "프리셋", "명백", "CUSTOM 통근 100% → 빠른 집", [h(A, 500_000, 50_000, 45), h(B, 900_000, 50_000, 25)],
         "CUSTOM", (B,), weights=(0, 100, 0)),
    Case("F6", "프리셋", "판단", "25만 원 더 내고 편도 15분 단축 (COMMUTE)",
         [h(A, 450_000, 50_000, 40), h(B, 700_000, 50_000, 25)], "COMMUTE", None),

    # G. 제약·누락·극단값
    Case("G1", "제약·누락", "명백", "예산 60만 → B(65만) 제외", [h(A, 550_000, 50_000, 40), h(B, 600_000, 50_000, 20)], "COMMUTE", (A,),
         options={"max_monthly_cost_won": 600_000}),
    Case("G2", "제약·누락", "명백", "통근 상한 40분 → A(45분) 제외", [h(A, 400_000, 50_000, 45), h(B, 600_000, 50_000, 35)], "COST", (B,),
         options={"max_commute_minutes": 40}),
    Case("G3", "제약·누락", "명백", "B 관리비 모름 → 순위 보류", [h(A, 500_000, 50_000, 30), h(B, 450_000, None, 30)], "BALANCED", ()),
    Case("G4", "제약·누락", "명백", "A 약국 정보만 없음, 나머지는 A가 우세",
         [h(A, 450_000, 50_000, 25, 5, 5, None), h(B, 550_000, 50_000, 35, 10, 10, 10)], "BALANCED", (A,)),
    Case("G5", "제약·누락", "명백", "두 집 모두 예산 초과 → 결과 없음", [h(A, 700_000, 50_000, 30), h(B, 800_000, 50_000, 20)], "BALANCED", (),
         options={"max_monthly_cost_won": 600_000}),
    Case("G6", "제약·누락", "명백", "둘 다 통근 120분 초과, 비용 같으면 덜 먼 A",
         [h(A, 300_000, 50_000, 130), h(B, 300_000, 50_000, 140)], "BALANCED", (A,)),
]


def oracle(case: Case) -> tuple:
    rate = (case.options or {}).get("deposit_annual_rate", DEPOSIT_RATE)
    burdens = sorted(case.houses, key=lambda house: persona_monthly_burden(house, case.priority, rate))
    return tuple(house.id for house in burdens)


def burden_gap(case: Case) -> int:
    rate = (case.options or {}).get("deposit_annual_rate", DEPOSIT_RATE)
    values = sorted(persona_monthly_burden(house, case.priority, rate) for house in case.houses)
    return round(values[1] - values[0])


def main() -> None:
    rows, by_category, by_kind = [], {}, {}
    for case in CASES:
        expected = case.expected if case.expected is not None else oracle(case)[:1]
        kwargs = dict(case.options or {})
        if case.weights:
            kwargs["weights"] = case.weights
        result = recommend(case.houses, case.priority, **kwargs)
        got = tuple(house.id for house in result.ranked)
        ok = got[: len(expected)] == expected if expected else got == ()
        # 순서를 뒤집어도 같은 결과인지
        stable = recommend(case.houses[::-1], case.priority, **kwargs).ranked == result.ranked
        rows.append((case, expected, got, ok and stable))
        for key, bucket in ((case.category, by_category), (case.kind, by_kind)):
            bucket.setdefault(key, [0, 0])
            bucket[key][0] += ok and stable
            bucket[key][1] += 1

    print(f"{'ID':<4}{'유형':<6}{'우선순위':<14}{'기대':<10}{'결과':<10}{'판정':<6}설명")
    for case, expected, got, ok in rows:
        note = case.description
        if case.kind == "판단":
            note += f"  [가상 사용자 월 손익 차 {burden_gap(case):,}원]"
        print(f"{case.id:<4}{case.kind:<6}{case.priority:<14}{'>'.join(expected) or '없음':<10}"
              f"{'>'.join(got) or '없음':<10}{'O' if ok else 'X':<6}{note}")
    total = sum(ok for *_, ok in rows)
    print(f"\n전체 {total}/{len(rows)} ({total / len(rows):.0%})")
    for title, bucket in (("유형별", by_kind), ("분류별", by_category)):
        print(title + ": " + ", ".join(f"{k} {a}/{b}" for k, (a, b) in bucket.items()))


if __name__ == "__main__":
    main()
