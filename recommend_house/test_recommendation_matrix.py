import unittest

from recommendation import House, recommend


def home(id, rent, fee, commute, mart, store, pharmacy, *, deposit=0, support=0):
    return House(
        id, rent, fee, commute, mart,
        deposit_won=deposit,
        convenience_store_walk_minutes=store,
        pharmacy_walk_minutes=pharmacy,
        monthly_support_won=support,
    )


class RecommendationMatrixTests(unittest.TestCase):
    def check_case(self, houses, expected_ids, priority="BALANCED", **options):
        result = recommend(houses, priority, **options)
        self.assertEqual(tuple(house.id for house in result.ranked), expected_ids)
        self.assertEqual(result, recommend(houses[::-1], priority, **options))
        return result

    def test_dominance_in_all_three_dimensions(self):
        for offset in range(10):
            with self.subTest(offset=offset):
                houses = [
                    home("A", 445_000 + 83_000 * offset, 65_000, 19 + 7 * offset, 5, 4, 7,
                         deposit=10_000_000),
                    home("B", 502_000 + 83_000 * offset, 70_000, 24 + 7 * offset, 9, 7, 11,
                         deposit=10_000_000),
                    home("C", 550_000 + 83_000 * offset, 80_000, 30 + 7 * offset, 14, 12, 18,
                         deposit=10_000_000),
                ]
                for priority in ("BALANCED", "COST", "COMMUTE", "NEIGHBORHOOD"):
                    self.check_case(houses, ("A", "B", "C"), priority)

    def test_limits_at_exact_value_and_one_unit_over(self):
        for offset in range(10):
            with self.subTest(offset=offset):
                budget = 675_000 + offset * 38_000
                commute = 28 + offset * 4
                houses = [
                    home("A", budget - 75_000 + 50_000, 75_000, commute, 11, 9, 12, support=50_000),
                    home("B", budget - 61_999, 62_000, commute - 1, 6, 4, 5),
                    home("C", budget - 74_000, 73_000, commute + 1, 8, 6, 9),
                ]
                result = self.check_case(
                    houses, ("A",), "COMMUTE",
                    max_monthly_cost_won=budget, max_commute_minutes=commute,
                )
                self.assertEqual(result.excluded_fields, {
                    "B": ("monthly_cost_won",), "C": ("commute_minutes",),
                })

    def test_unknown_and_known_violations_can_coexist(self):
        for offset in range(10):
            with self.subTest(offset=offset):
                budget = 610_000 + offset * 27_000
                unknown_rent = budget + (-12_000 if offset % 2 == 0 else 1)
                houses = [
                    home("A", unknown_rent, None, 27, None, 5, 9),
                    home("B", budget - 86_000, 80_000, 41, 9, 6, 10),
                    home("C", budget - 53_000, 56_000, 48, 8, 5, 7),
                ]
                eligible = ("B",) if offset % 2 else ()
                result = self.check_case(houses, eligible, max_monthly_cost_won=budget)
                expected_exclusions = {"C": ("monthly_cost_won",)}
                if offset % 2:
                    expected_exclusions["A"] = ("monthly_cost_won",)
                self.assertEqual(result.excluded_fields, expected_exclusions)
                self.assertEqual(result.missing_fields, {"A": ("maintenance_won",)})
                self.assertEqual(result.estimated_fields, {"A": ("supermarket_walk_minutes",)})

    def test_cost_and_commute_beyond_reference_values_still_rank(self):
        for offset in range(10):
            with self.subTest(offset=offset):
                cost = 1_036_000 + offset * 39_000
                commute = 123 + offset * 3
                houses = [
                    home("A", cost, 52_000, commute + 3, 8, 6, 13),
                    home("B", cost + 120_000, 52_000, commute, 8, 6, 13),
                ]
                for priority in ("BALANCED", "COMMUTE"):
                    result = self.check_case(houses, ("A", "B"), priority)
                    for house in result.ranked:
                        self.assertEqual((house.cost_score, house.time_score), (0.0, 0.0))

    def test_priority_switch_reports_round_trip_savings(self):
        for offset in range(10):
            with self.subTest(offset=offset):
                rent = 465_000 + offset * 19_000
                commute = 33 + offset
                days = 9 + offset * 2
                houses = [
                    home("A", rent, 48_000, commute, 13, 5, 6),
                    home("B", rent + 176_000, 55_000, commute - 11, 13, 5, 6),
                ]
                self.check_case(houses, ("A", "B"))
                result = self.check_case(houses, ("B", "A"), "COMMUTE", commute_days=days)
                self.assertEqual(result.tradeoff.compared_house_id, "A")
                self.assertEqual(result.tradeoff.extra_monthly_cost_won, 183_000)
                self.assertEqual(result.tradeoff.monthly_hours_saved, round(22 * days / 60, 1))

    def test_fixed_priorities_against_personal_willingness_to_pay(self):
        # 편도 10분 단축의 손익분기: BALANCED 72,917원, COMMUTE 250,000원.
        # 개인 지불 의향은 모델과 무관하게 정한 가상 값이다.
        cases = [
            ("BALANCED", 50_000, 60_000, "B", True),
            ("BALANCED", 60_000, 40_000, "B", False),
            ("BALANCED", 80_000, 100_000, "A", False),
            ("BALANCED", 90_000, 70_000, "A", True),
            ("BALANCED", 120_000, 110_000, "A", True),
            ("COMMUTE", 150_000, 200_000, "B", True),
            ("COMMUTE", 200_000, 120_000, "B", False),
            ("COMMUTE", 240_000, 260_000, "B", True),
            ("COMMUTE", 260_000, 300_000, "A", False),
            ("COMMUTE", 300_000, 280_000, "A", True),
        ]
        for index, (priority, premium, willingness, model_winner, agrees) in enumerate(cases):
            with self.subTest(priority=priority, premium=premium, willingness=willingness):
                rent = 395_000 + index * 13_000
                commute = 29 + index
                houses = [
                    home("A", rent, 62_000, commute, 11, 7, 14),
                    home("B", rent + premium, 62_000, commute - 10, 11, 7, 14),
                ]
                other = "A" if model_winner == "B" else "B"
                self.check_case(houses, (model_winner, other), priority)
                personal_winner = "B" if premium <= willingness else "A"
                self.assertEqual(model_winner == personal_winner, agrees)
