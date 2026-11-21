# 첫집내비 집 종합 추천 개발자 가이드

`recommendation.py`는 매물 2~3곳의 월 주거비, 편도 통근 시간, 생활 편의를 비교합니다. 표준 라이브러리만 사용하며, Python 3.11에서 실행을 확인했습니다. 호출 쪽에서 관리비, 길찾기 결과, 시설 도보 시간을 준비합니다.

## 호출 예시

```python
from dataclasses import asdict
from recommendation import House, recommend

houses = [
    House(
        id="A",
        rent_won=490_000,
        maintenance_won=55_000,
        commute_minutes=37,
        supermarket_walk_minutes=9,
        subway_walk_minutes=7,
    ),
    House(
        id="B",
        rent_won=660_000,
        maintenance_won=65_000,
        commute_minutes=25,
        supermarket_walk_minutes=9,
        subway_walk_minutes=7,
    ),
]

result = recommend(
    houses,
    "시간이 먼저",
    commute_days=18,
    max_monthly_cost_won=800_000,
    max_commute_minutes=40,
)

assert result.ranked[0].id == "B"
assert result.tradeoff.extra_monthly_cost_won == 180_000
assert result.tradeoff.monthly_hours_saved == 7.2
payload = asdict(result)
```

- `House.id`는 비어 있지 않은 고유 문자열입니다. 금액은 원, 시간은 분 단위의 0 이상 정수입니다.
- 월세는 필수입니다. 관리비나 이동 시간을 모르면 `None`으로 전달합니다. 실제 값이 0이면 `0`을 전달합니다.
- `priority`의 기본값은 `"균형"`입니다. `commute_days`는 월 출근일 수로, 기본값은 20이고 허용 범위는 0~31일입니다. 출근일은 시간 절약 설명에만 사용됩니다.
- `max_monthly_cost_won`은 지원 전 월세와 관리비 합계의 상한입니다. `max_commute_minutes`는 편도 통근 시간 상한입니다. 각 인자의 기본값은 `None`이며, 상한과 같은 값은 허용합니다.
- 잘못된 후보 수, 중복 ID, 잘못된 우선순위, 음수 또는 정수가 아닌 수치 입력은 `ValueError`로 처리합니다.

## 결과 연결

| 필드 | 화면에서 사용할 값 |
|---|---|
| `ranked` | 정렬된 후보 ID, 지원 전 월 주거비, 비용·통근·생활 편의 점수와 종합 점수 |
| `missing_fields` | 후보 ID별 확인할 입력 필드 목록 |
| `excluded_fields` | 후보 ID별 상한 초과 사유. 키 값은 `monthly_cost_won` 또는 `commute_minutes` |
| `tradeoff` | 현재 우선순위의 1위가 같은 후보군의 균형 1위와 다를 때, 비교 후보 ID와 월 비용·통근 시간 차이 |

한 매물에 누락과 상한 위반이 동시에 표시될 수 있습니다. 예를 들어 관리비를 몰라도 월세만으로 예산을 초과하면 제외 사유가 생깁니다. 순위를 계산할 수 있는 후보가 두 곳 이상이면 해당 후보를 정렬합니다. 한 곳만 남았을 때는 다른 후보가 모두 상한 위반으로 제외된 경우에만 그 한 곳을 반환합니다. `ranked`가 비어 있으면 두 사유 맵을 함께 확인해 매물 입력 보완 또는 조건 변경을 안내할 수 있습니다.

`tradeoff.extra_monthly_cost_won`은 현재 1위 월 주거비에서 균형 1위 월 주거비를 뺀 값입니다. `monthly_hours_saved`는 두 집의 편도 통근 시간 차이에 왕복 2회와 월 출근일을 곱한 뒤 시간으로 환산합니다. 차이는 부호가 있는 값으로 처리합니다.

## 점수와 순위

| 우선순위 | 비용 | 통근 | 생활 편의 |
|---|---:|---:|---:|
| 균형 | 40% | 35% | 25% |
| 시간이 먼저 | 30% | 55% | 15% |

- 비용 점수는 `100 × (1 − 월 주거비 / 1,000,000)`입니다. 월 주거비는 지원 전 월세와 관리비 합계입니다.
- 통근 점수는 `100 × (1 − 편도 통근 분 / 60)`입니다.
- 생활 편의 점수는 대형마트와 지하철역의 도보 점수 평균입니다. 시설별 도보 점수는 `max(0, 100 × (1 − 도보 분 / 30))`입니다.
- 비용·통근·종합 점수는 음수가 될 수 있습니다. 순위에는 반올림 전 종합 점수를 쓰고, 정확히 동점이면 월 주거비가 낮은 순, 편도 통근 시간이 짧은 순, ID 오름차순으로 정렬합니다. 반환 점수는 소수점 첫째 자리까지 반올림합니다.

가중치는 모드별 고정값입니다. 월 주거비 상한은 총비용 제약이며, 일정 통근 시간을 줄이기 위해 얼마를 더 낼지에 대한 개인별 선호는 현재 API 입력에 없습니다. 보증금, 교통비, 지원금, 매물 상태는 종합 점수 산정 범위 밖입니다.

## 검증 재실행

```powershell
python -B -m unittest -v test_recommendation_matrix
python -B -m unittest -v
```

신규 테스트 파일은 가상 매물 비교 60건을 6개 테스트 메서드의 하위 사례로 실행합니다. 후보 순서를 뒤집어도 같은 결과가 나오는지 각 비교에서 확인합니다. 일반 동작 50건은 기대 결과와 일치했습니다. 가정한 개인별 지불 의향을 대조한 10건 중 5건은 고정 가중치의 추천과 달랐습니다. 테스트에는 이 차이가 드러난 사례도 기록돼 있습니다. 이 수치는 실제 사용자 조사 결과가 아닙니다.
