"""첫집내비 3-2 집 종합 추천. 금액은 원, 이동 시간은 편도 분 단위."""

from dataclasses import dataclass, field
from fractions import Fraction
from typing import Literal, Sequence

Priority = Literal["균형", "시간이 먼저"]
WEIGHTS: dict[Priority, tuple[int, int, int]] = {
    "균형": (40, 35, 25),
    "시간이 먼저": (30, 55, 15),
}


@dataclass(frozen=True)
class House:
    id: str
    rent_won: int
    maintenance_won: int | None
    commute_minutes: int | None
    supermarket_walk_minutes: int | None
    subway_walk_minutes: int | None


@dataclass(frozen=True)
class RankedHouse:
    id: str
    monthly_cost_won: int
    cost_score: float
    time_score: float
    convenience_score: float
    total_score: float


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


def _score(value: int, zero_score_at: int) -> Fraction:
    return Fraction(100 * max(0, zero_score_at - value), zero_score_at)


def _score_with_penalty(value: int, zero_score_at: int) -> Fraction:
    return Fraction(100 * (zero_score_at - value), zero_score_at)


def recommend(
    houses: Sequence[House],
    priority: Priority = "균형",
    *,
    commute_days: int = 20,
    max_monthly_cost_won: int | None = None,
    max_commute_minutes: int | None = None,
) -> Recommendation:
    """필수 상한을 적용한 뒤 정렬한다. 비용·통근 점수는 음수가 될 수 있다."""
    if not 2 <= len(houses) <= 3:
        raise ValueError("후보 집을 2~3곳 입력하세요.")
    if any(not house.id.strip() for house in houses):
        raise ValueError("후보 집 ID를 입력하세요.")
    if len({house.id for house in houses}) != len(houses):
        raise ValueError("후보 집 ID는 중복될 수 없습니다.")
    if priority not in WEIGHTS:
        raise ValueError("지원하는 우선순위는 균형과 시간이 먼저입니다.")
    if type(commute_days) is not int or not 0 <= commute_days <= 31:
        raise ValueError("월 통근일 수는 0~31일의 정수여야 합니다.")
    for name, limit in (
        ("max_monthly_cost_won", max_monthly_cost_won),
        ("max_commute_minutes", max_commute_minutes),
    ):
        if limit is not None and (type(limit) is not int or limit < 0):
            raise ValueError(f"{name}: 상한은 0 이상의 정수여야 합니다.")

    missing: dict[str, tuple[str, ...]] = {}
    excluded: dict[str, tuple[str, ...]] = {}
    scores: dict[str, tuple[Fraction, Fraction, Fraction]] = {}
    costs: dict[str, int] = {}
    commutes: dict[str, int] = {}
    for house in houses:
        fields = {
            "maintenance_won": house.maintenance_won,
            "commute_minutes": house.commute_minutes,
            "supermarket_walk_minutes": house.supermarket_walk_minutes,
            "subway_walk_minutes": house.subway_walk_minutes,
        }
        if type(house.rent_won) is not int or house.rent_won < 0:
            raise ValueError(f"{house.id}: 월세는 0 이상의 정수여야 합니다.")
        for field, value in fields.items():
            if value is not None and (type(value) is not int or value < 0):
                raise ValueError(f"{house.id}: {field}는 0 이상의 정수여야 합니다.")
        absent = tuple(field for field, value in fields.items() if value is None)
        if absent:
            missing[house.id] = absent
        violations = []
        if (
            max_monthly_cost_won is not None
            and house.rent_won + (house.maintenance_won or 0) > max_monthly_cost_won
        ):
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

        maintenance, commute, supermarket, subway = fields.values()
        assert maintenance is not None and commute is not None
        assert supermarket is not None and subway is not None
        cost = house.rent_won + maintenance
        costs[house.id] = cost
        commutes[house.id] = commute
        scores[house.id] = (
            _score_with_penalty(cost, 1_000_000),
            _score_with_penalty(commute, 60),
            (_score(supermarket, 30) + _score(subway, 30)) / 2,
        )

    if not scores or (len(scores) == 1 and len(excluded) != len(houses) - 1):
        return Recommendation((), missing, None, excluded)

    def rank(mode: Priority) -> list[tuple[str, Fraction]]:
        totals = [
            (house_id, sum(score * weight for score, weight in zip(parts, WEIGHTS[mode])) / 100)
            for house_id, parts in scores.items()
        ]
        return sorted(totals, key=lambda item: (-item[1], costs[item[0]], commutes[item[0]], item[0]))

    ordered = rank(priority)
    ranked = tuple(
        RankedHouse(house_id, costs[house_id], *(round(float(score), 1) for score in scores[house_id]),
                    round(float(total), 1))
        for house_id, total in ordered
    )
    winner = ranked[0].id
    balanced_winner = rank("균형")[0][0]
    tradeoff = None
    if winner != balanced_winner:
        tradeoff = Tradeoff(
            compared_house_id=balanced_winner,
            extra_monthly_cost_won=costs[winner] - costs[balanced_winner],
            monthly_hours_saved=round((commutes[balanced_winner] - commutes[winner]) * 2 * commute_days / 60, 1),
        )
    return Recommendation(ranked, missing, tradeoff, excluded)
