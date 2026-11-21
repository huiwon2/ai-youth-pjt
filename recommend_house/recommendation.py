"""첫집내비 3-2 집 종합 추천. 금액은 원, 이동 시간은 편도 분 단위."""

from dataclasses import KW_ONLY, dataclass, field
from fractions import Fraction
from typing import Literal, Sequence

Preset = Literal["BALANCED", "COST", "COMMUTE", "NEIGHBORHOOD", "CUSTOM", "균형", "시간이 먼저"]

# 백엔드 PriorityPreset(명세 고정값)과 같은 (비용, 통근, 생활 편의) 가중치. CUSTOM은 weights 인자로 받는다.
PRESET_WEIGHTS: dict[str, tuple[int, int, int]] = {
    "BALANCED": (40, 35, 25),
    "COST": (60, 25, 15),
    "COMMUTE": (20, 60, 20),
    "NEIGHBORHOOD": (30, 20, 50),
}
# 이전 버전의 우선순위 이름. "시간이 먼저"(30/55/15)는 가장 가까운 백엔드 프리셋 COMMUTE로 바꿔 계산한다.
LEGACY_PRIORITY_ALIASES = {"균형": "BALANCED", "시간이 먼저": "COMMUTE"}

# 점수가 0이 되는 기준값. 가중치가 고정이라 두 기준값의 비율이 '통근 1분의 값'을 정한다.
# BALANCED에서 편도 1분 단축 = 월 약 7,292원이고, 월 20일 출근이면 시간당 약 10,900원으로
# 2026년 최저임금(시간당 10,320원)과 비슷하다. 기준값을 바꾸면 테스트의 손익분기 사례도 바뀐다.
COST_ZERO_SCORE_WON = 1_000_000
COMMUTE_ZERO_SCORE_MINUTES = 120

# 생활 편의는 자주 가는 근거리 시설만 본다. 지하철역 도보는 길찾기 통근 시간의 도보 구간에
# 이미 들어 있어 이중 반영을 피하려고 넣지 않는다.
# 시설별 월 왕복 이용 횟수(편의점 주 3회, 마트 주 2회, 약국 월 2회). 생활 편의 점수는 이 횟수로 가중 평균한다.
FACILITY_MONTHLY_TRIPS = {
    "supermarket_walk_minutes": 8,
    "convenience_store_walk_minutes": 12,
    "pharmacy_walk_minutes": 2,
}
FACILITY_FIELDS = tuple(FACILITY_MONTHLY_TRIPS)
# 도보 점수가 0이 되는 시간. BALANCED에서 시설 도보 1분(이용 횟수 반영)이 통근 1분과 비슷한
# 시간 가치(시간당 약 10,700원)가 되도록 정했다. 30분처럼 짧으면 생활 편의가 돈보다 2~3배 크게 반영된다.
WALK_ZERO_SCORE_MINUTES = 80
# 시설 도보 시간을 하나도 모를 때 쓰는 중간값.
NEUTRAL_CONVENIENCE_SCORE = Fraction(50)

# 보증금을 월 비용으로 환산할 때 쓰는 연 기회비용률(전월세전환율 수준). 호출 쪽에서 바꿀 수 있다.
DEFAULT_DEPOSIT_ANNUAL_RATE = 0.045
MAX_DEPOSIT_ANNUAL_RATE = 0.2

REQUIRED_FOR_RANKING = ("maintenance_won", "commute_minutes")
ESTIMABLE_FIELDS = FACILITY_FIELDS
# 이전 버전 호환용으로 받기만 하고 점수에는 쓰지 않는 필드.
IGNORED_FIELDS = ("subway_walk_minutes",)


@dataclass(frozen=True)
class House:
    # 위치 인자 순서는 이전 버전과 같다. 새 필드는 키워드로만 받는다.
    id: str
    rent_won: int
    maintenance_won: int | None
    commute_minutes: int | None
    supermarket_walk_minutes: int | None
    subway_walk_minutes: int | None = None  # 호환용. 통근 시간에 이미 들어 있어 점수에 쓰지 않는다.
    _: KW_ONLY
    deposit_won: int = 0
    convenience_store_walk_minutes: int | None = None
    pharmacy_walk_minutes: int | None = None
    monthly_support_won: int = 0


@dataclass(frozen=True)
class RankedHouse:
    # 앞의 6개 필드는 이전 버전과 같은 순서·의미다. monthly_cost_won은 지원 전 월세 + 관리비.
    id: str
    monthly_cost_won: int
    cost_score: float
    time_score: float
    convenience_score: float
    total_score: float
    effective_monthly_cost_won: int = 0
    monthly_after_support_won: int = 0
    deposit_cost_won: int = 0


@dataclass(frozen=True)
class Tradeoff:
    compared_house_id: str
    extra_monthly_cost_won: int
    monthly_hours_saved: float


@dataclass(frozen=True)
class Recommendation:
    ranked: tuple[RankedHouse, ...]
    missing_fields: dict[str, tuple[str, ...]]
    tradeoff: Tradeoff | None
    excluded_fields: dict[str, tuple[str, ...]] = field(default_factory=dict)
    estimated_fields: dict[str, tuple[str, ...]] = field(default_factory=dict)


@dataclass(frozen=True)
class _Scored:
    housing: int
    cost: Fraction
    after_support: int
    deposit_cost: Fraction
    commute: int
    parts: tuple[Fraction, Fraction, Fraction]


def _is_count(value: object) -> bool:
    return type(value) is int and value >= 0


def _score(value: int, zero_score_at: int) -> Fraction:
    return Fraction(100 * max(0, zero_score_at - value), zero_score_at)


def _score_with_penalty(value: Fraction | int, zero_score_at: int) -> Fraction:
    return 100 * (zero_score_at - Fraction(value)) / zero_score_at


def _display(score: Fraction) -> float:
    return round(float(min(Fraction(100), max(Fraction(0), score))), 1)


def _resolve_weights(priority: str, weights: Sequence[int] | None) -> tuple[int, int, int]:
    priority = LEGACY_PRIORITY_ALIASES.get(priority, priority)
    if priority == "CUSTOM":
        if (
            weights is None
            or len(weights) != 3
            or any(not _is_count(weight) or weight > 100 for weight in weights)
            or sum(weights) != 100
        ):
            raise ValueError("CUSTOM은 0~100 정수 3개(비용, 통근, 생활 편의)의 합이 100인 weights가 필요합니다.")
        return tuple(weights)  # type: ignore[return-value]
    if priority not in PRESET_WEIGHTS:
        raise ValueError("지원하는 우선순위는 BALANCED, COST, COMMUTE, NEIGHBORHOOD, CUSTOM(이전 이름 균형, 시간이 먼저)입니다.")
    if weights is not None:
        raise ValueError("weights는 CUSTOM에서만 보낼 수 있습니다.")
    return PRESET_WEIGHTS[priority]


def recommend(
    houses: Sequence[House],
    priority: Preset = "BALANCED",
    *,
    weights: Sequence[int] | None = None,
    commute_days: int = 20,
    deposit_annual_rate: float = DEFAULT_DEPOSIT_ANNUAL_RATE,
    max_monthly_cost_won: int | None = None,
    max_commute_minutes: int | None = None,
) -> Recommendation:
    """필수 상한을 적용한 뒤 정렬한다. 순위는 0~100으로 자르기 전 점수로 정한다."""
    if not 2 <= len(houses) <= 3:
        raise ValueError("후보 집을 2~3곳 입력하세요.")
    if any(not isinstance(house.id, str) or not house.id.strip() for house in houses):
        raise ValueError("후보 집 ID를 입력하세요.")
    if len({house.id for house in houses}) != len(houses):
        raise ValueError("후보 집 ID는 중복될 수 없습니다.")
    mode_weights = _resolve_weights(priority, weights)
    if type(commute_days) is not int or not 0 <= commute_days <= 31:
        raise ValueError("월 통근일 수는 0~31일의 정수여야 합니다.")
    if type(deposit_annual_rate) not in (int, float) or not 0 <= deposit_annual_rate <= MAX_DEPOSIT_ANNUAL_RATE:
        raise ValueError("보증금 환산율은 0~0.2 사이의 숫자여야 합니다.")
    monthly_deposit_rate = Fraction(str(deposit_annual_rate)) / 12
    for name, limit in (
        ("max_monthly_cost_won", max_monthly_cost_won),
        ("max_commute_minutes", max_commute_minutes),
    ):
        if limit is not None and not _is_count(limit):
            raise ValueError(f"{name}: 상한은 0 이상의 정수여야 합니다.")

    missing: dict[str, tuple[str, ...]] = {}
    estimated: dict[str, tuple[str, ...]] = {}
    excluded: dict[str, tuple[str, ...]] = {}
    scored: dict[str, _Scored] = {}
    for house in houses:
        for name in ("deposit_won", "rent_won", "monthly_support_won"):
            if not _is_count(getattr(house, name)):
                raise ValueError(f"{house.id}: {name}는 0 이상의 정수여야 합니다.")
        for name in (*REQUIRED_FOR_RANKING, *ESTIMABLE_FIELDS, *IGNORED_FIELDS):
            value = getattr(house, name)
            if value is not None and not _is_count(value):
                raise ValueError(f"{house.id}: {name}는 0 이상의 정수여야 합니다.")

        absent = tuple(name for name in REQUIRED_FOR_RANKING if getattr(house, name) is None)
        if absent:
            missing[house.id] = absent
        guessed = tuple(name for name in ESTIMABLE_FIELDS if getattr(house, name) is None)
        if guessed:
            estimated[house.id] = guessed

        # 관리비를 모르면 0원으로 둔 하한값이라, 이 값만으로도 예산 초과면 확실히 초과다.
        housing = house.rent_won + (house.maintenance_won or 0)
        after_support = max(0, housing - house.monthly_support_won)
        violations = []
        if max_monthly_cost_won is not None and after_support > max_monthly_cost_won:
            violations.append("monthly_cost_won")
        if (
            max_commute_minutes is not None
            and house.commute_minutes is not None
            and house.commute_minutes > max_commute_minutes
        ):
            violations.append("commute_minutes")
        if violations:
            excluded[house.id] = tuple(violations)
        if absent or violations:
            continue

        assert house.commute_minutes is not None
        # 모르는 시설은 빼고, 아는 시설의 이용 횟수로 가중 평균한다.
        known = [
            (trips, getattr(house, name))
            for name, trips in FACILITY_MONTHLY_TRIPS.items()
            if getattr(house, name) is not None
        ]
        convenience = (
            sum((trips * _score(minutes, WALK_ZERO_SCORE_MINUTES) for trips, minutes in known), Fraction(0))
            / sum(trips for trips, _ in known)
            if known
            else NEUTRAL_CONVENIENCE_SCORE
        )
        deposit_cost = house.deposit_won * monthly_deposit_rate
        # 교통비는 주거비 비교에 넣지 않는다(백엔드 명세: 참고 교통비는 주거비 합계에 더하지 않음).
        cost = after_support + deposit_cost
        scored[house.id] = _Scored(
            housing=housing,
            cost=cost,
            after_support=after_support,
            deposit_cost=deposit_cost,
            commute=house.commute_minutes,
            parts=(
                _score_with_penalty(cost, COST_ZERO_SCORE_WON),
                _score_with_penalty(house.commute_minutes, COMMUTE_ZERO_SCORE_MINUTES),
                convenience,
            ),
        )

    # 다른 후보가 누락으로 빠져 한 곳만 남으면 그 후보가 더 나을 수 있어 순위를 내지 않는다.
    if not scored or (len(scored) == 1 and len(excluded) != len(houses) - 1):
        return Recommendation((), missing, None, excluded, estimated)

    def rank(mode_weights: tuple[int, int, int]) -> list[tuple[str, Fraction]]:
        totals = [
            (house_id, sum((part * weight for part, weight in zip(item.parts, mode_weights)), Fraction(0)) / 100)
            for house_id, item in scored.items()
        ]
        return sorted(
            totals,
            key=lambda pair: (-pair[1], scored[pair[0]].cost, scored[pair[0]].commute, pair[0]),
        )

    ranked = tuple(
        RankedHouse(
            id=house_id,
            monthly_cost_won=scored[house_id].housing,
            effective_monthly_cost_won=round(scored[house_id].cost),
            monthly_after_support_won=scored[house_id].after_support,
            deposit_cost_won=round(scored[house_id].deposit_cost),
            cost_score=_display(scored[house_id].parts[0]),
            time_score=_display(scored[house_id].parts[1]),
            convenience_score=_display(scored[house_id].parts[2]),
            total_score=_display(total),
        )
        for house_id, total in rank(mode_weights)
    )
    winner = scored[ranked[0].id]
    balanced_winner_id = rank(PRESET_WEIGHTS["BALANCED"])[0][0]
    tradeoff = None
    if ranked[0].id != balanced_winner_id:
        balanced_winner = scored[balanced_winner_id]
        tradeoff = Tradeoff(
            compared_house_id=balanced_winner_id,
            extra_monthly_cost_won=round(winner.cost - balanced_winner.cost),
            monthly_hours_saved=round((balanced_winner.commute - winner.commute) * 2 * commute_days / 60, 1),
        )
    return Recommendation(ranked, missing, tradeoff, excluded, estimated)
