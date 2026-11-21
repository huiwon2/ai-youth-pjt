# 첫집내비 집 종합 추천 개발자 가이드

`recommendation.py`는 매물 2~3곳의 월 실부담 비용, 편도 통근 시간, 생활 편의를 비교합니다. 표준 라이브러리만 사용하며, Python 3.11에서 실행을 확인했습니다. 호출 쪽에서 관리비, 지원액, 길찾기 결과(시간·요금), 시설 도보 시간을 준비합니다.

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
        deposit_won=10_000_000,
        convenience_store_walk_minutes=3,
        pharmacy_walk_minutes=6,
        monthly_support_won=0,
    ),
    House(
        id="B",
        rent_won=660_000,
        maintenance_won=65_000,
        commute_minutes=25,
        supermarket_walk_minutes=9,
        deposit_won=10_000_000,
        convenience_store_walk_minutes=3,
        pharmacy_walk_minutes=6,
        monthly_support_won=0,
    ),
]

result = recommend(
    houses,
    "COMMUTE",
    commute_days=18,
    max_monthly_cost_won=800_000,
    max_commute_minutes=40,
)

assert result.ranked[0].id == "B"
assert result.tradeoff.extra_monthly_cost_won == 180_000
assert result.tradeoff.monthly_hours_saved == 7.2
payload = asdict(result)
```

## 입력

- `House.id`는 비어 있지 않은 고유 문자열입니다. 금액은 원, 시간은 분 단위의 0 이상 정수입니다.
- 위치 인자는 이전 버전과 같은 `House(id, rent_won, maintenance_won, commute_minutes, supermarket_walk_minutes, subway_walk_minutes)` 순서입니다. 그 뒤의 `deposit_won`, `convenience_store_walk_minutes`, `pharmacy_walk_minutes`, `monthly_support_won`은 키워드로만 받습니다.
- 월세(`rent_won`)는 필수입니다. 보증금(`deposit_won`)과 그 집에 적용되는 월 지원액(`monthly_support_won`)의 기본값은 0입니다. 보증금을 넘기지 않으면 보증금 차이가 비교에 반영되지 않으니 새 호출에서는 꼭 전달하세요.
- `subway_walk_minutes`는 호환용으로 받아 값만 검사하고 점수에는 쓰지 않습니다. 길찾기 통근 시간의 도보 구간에 이미 들어 있기 때문입니다.
- 관리비나 통근 시간을 모르면 `None`으로 전달합니다. 이 두 값이 없으면 해당 집은 순위에서 빠지고 `missing_fields`에 표시됩니다.
- 시설 도보 시간(마트, 편의점, 약국)을 모르면 `None`으로 전달합니다. 이 경우 집은 순위에 남고 `estimated_fields`에 표시됩니다. 실제 값이 0이면 `0`을 전달합니다.
- `priority`는 백엔드 `PriorityPreset`과 같은 `BALANCED`(기본값), `COST`, `COMMUTE`, `NEIGHBORHOOD`, `CUSTOM` 중 하나입니다. 이전 이름 `"균형"`은 `BALANCED`로, `"시간이 먼저"`는 `COMMUTE`로 바꿔 계산합니다(가중치는 30/55/15가 아니라 백엔드 명세의 20/60/20). `CUSTOM`일 때만 `weights=(비용, 통근, 생활 편의)`를 보내며, 각 값은 0~100 정수이고 합이 100이어야 합니다.
- `commute_days`는 월 출근일 수로, 기본값은 20이고 허용 범위는 0~31일입니다. 맞교환 설명의 시간 계산에만 사용되며 순위와 비용에는 영향을 주지 않습니다.
- `deposit_annual_rate`는 보증금을 월 비용으로 환산하는 연 비율입니다. 기본값은 0.045(4.5%)이고 허용 범위는 0~0.2입니다. 0이면 보증금을 비교에서 제외합니다.
- `max_monthly_cost_won`은 지원 후 매달 나가는 주거비(월세 + 관리비 − 월 지원액)의 상한입니다. 보증금 환산액은 포함하지 않습니다. `max_commute_minutes`는 편도 통근 시간 상한입니다. 기본값은 모두 `None`이며, 상한과 같은 값은 허용합니다.
- 잘못된 후보 수, 중복 ID, 잘못된 우선순위나 가중치, 음수 또는 정수가 아닌 수치 입력은 `ValueError`로 처리합니다.

## 결과 연결

| 필드 | 화면에서 사용할 값 |
|---|---|
| `ranked` | 정렬된 후보 ID, 지원 전 월 주거비, 비용·통근·생활 편의 점수와 종합 점수, 비용 내역(아래 표) |
| `missing_fields` | 후보 ID별로 순위 계산을 막는 입력 필드(`maintenance_won`, `commute_minutes`) |
| `estimated_fields` | 후보 ID별로 추정해서 계산한 입력 필드(시설 도보 시간) |
| `excluded_fields` | 후보 ID별 상한 초과 사유. 값은 `monthly_cost_won` 또는 `commute_minutes` |
| `tradeoff` | 현재 우선순위의 1위가 같은 후보군의 BALANCED 1위와 다를 때, 비교 후보 ID와 월 비용·통근 시간 차이 |

`RankedHouse` 비용 필드:

| 필드 | 의미 |
|---|---|
| `monthly_cost_won` | 지원 전 월세 + 관리비 (이전 버전과 같은 의미) |
| `monthly_after_support_won` | max(0, 월세 + 관리비 − 월 지원액) |
| `deposit_cost_won` | 보증금 × 연 환산율 ÷ 12 (반올림) |
| `effective_monthly_cost_won` | 지원 후 주거비 + 보증금 환산액. 비용 점수와 맞교환 계산의 기준 |

한 매물에 누락과 상한 위반이 동시에 표시될 수 있습니다. 예를 들어 관리비를 몰라도 월세만으로 예산을 초과하면 제외 사유가 생깁니다. 순위를 계산할 수 있는 후보가 두 곳 이상이면 해당 후보를 정렬합니다. 한 곳만 남았을 때는 다른 후보가 모두 상한 위반으로 제외된 경우에만 그 한 곳을 반환합니다. 누락으로 빠진 후보가 더 나을 수도 있기 때문입니다. `ranked`가 비어 있으면 사유 맵을 함께 확인해 매물 입력 보완 또는 조건 변경을 안내할 수 있습니다.

`tradeoff.extra_monthly_cost_won`은 현재 1위의 `effective_monthly_cost_won`에서 BALANCED 1위의 값을 뺀 값입니다. `monthly_hours_saved`는 두 집의 편도 통근 시간 차이에 왕복 2회와 월 출근일을 곱한 뒤 시간으로 환산합니다. 두 값 모두 부호가 있습니다. 예를 들어 `COST`에서 더 싸고 먼 집이 1위가 되면 두 값이 모두 음수입니다.

## 점수와 순위

| 우선순위 | 비용 | 통근 | 생활 편의 | 편도 1분 단축의 가치 (월 20일 기준 시간당) |
|---|---:|---:|---:|---:|
| BALANCED | 40% | 35% | 25% | 월 7,292원 (약 10,900원) |
| COST | 60% | 25% | 15% | 월 3,472원 (약 5,200원) |
| COMMUTE | 20% | 60% | 20% | 월 25,000원 (약 37,500원) |
| NEIGHBORHOOD | 30% | 20% | 50% | 월 5,556원 (약 8,300원) |

- 비용 점수는 `100 × (1 − effective_monthly_cost_won / 1,000,000)`입니다.
- 통근 점수는 `100 × (1 − 편도 통근 분 / 120)`입니다.
- 가중치는 백엔드 명세 고정값이라, 두 기준값(100만 원, 120분)의 비율로 시간의 가치를 맞췄습니다. BALANCED의 시간당 가치가 2026년 최저임금(시간당 10,320원)과 비슷한 수준입니다. 기준값을 바꾸면 위 표와 `test_implied_value_of_one_commute_minute`도 함께 바꿔야 합니다.
- 생활 편의 점수는 시설별 도보 점수를 월 이용 횟수(편의점 12, 마트 8, 약국 2)로 가중 평균한 값입니다. 시설별 도보 점수는 `max(0, 100 × (1 − 도보 분 / 80))`입니다. 80분은 BALANCED에서 시설 도보 시간(이용 횟수 반영)이 통근 시간과 비슷한 시간 가치(시간당 약 10,700원)가 되도록 정한 값입니다. 모르는 시설은 가중 평균에서 빼고, 셋 다 모르면 50점을 씁니다. 지하철역 도보 시간은 길찾기 통근 시간의 도보 구간에 이미 들어 있어서 넣지 않습니다.
- 순위에는 반올림·자르기 전 종합 점수를 씁니다. 그래서 기준값을 넘는 비용이나 통근 시간도 계속 감점됩니다. 정확히 동점이면 `effective_monthly_cost_won`이 낮은 순, 편도 통근 시간이 짧은 순, ID 오름차순으로 정렬합니다.
- 반환 점수는 화면 표시용으로 0~100 범위로 자른 뒤 소수점 첫째 자리까지 반올림합니다. 그래서 기준값을 넘은 후보끼리는 표시 점수가 같아도 순위는 다를 수 있습니다.

일정 통근 시간을 줄이기 위해 얼마를 더 낼지에 대한 개인별 선호는 프리셋 가중치(또는 CUSTOM)로만 반영됩니다. 교통비, 매물 상태, 일회성 지원금은 종합 점수 산정 범위 밖입니다. 교통비는 백엔드 명세대로 주거비 합계에 더하지 않습니다.

## 검증 재실행

```powershell
python -B -m unittest -v test_recommendation_matrix
python -B -m unittest -v
```

정확도 검증 스크립트는 `python -B evaluate_accuracy.py`로 실행합니다. 알고리즘과 독립적으로 정답을 정한 36개 사례(상식으로 답이 정해지는 명백 사례 26개, 프리셋별 가상 사용자의 월 손익으로 정답을 정한 판단 사례 10개)를 비교하며, 현재 35/36을 맞힙니다. 틀리는 1건(F6)은 COMMUTE 프리셋 가중치(20/60/20, 명세 고정값)에 담긴 시간 가치(시간당 약 37,500원)가 가상 사용자 기준(2만 원)보다 높아서 생깁니다.

`test_recommendation_matrix`는 가상 매물 비교 60건을 6개 테스트 메서드의 하위 사례로 실행합니다. 후보 순서를 뒤집어도 같은 결과가 나오는지 각 비교에서 확인합니다. 가정한 개인별 지불 의향을 대조한 10건 중 4건은 고정 가중치의 추천과 달랐습니다. 이 수치는 실제 사용자 조사 결과가 아닙니다.
