import unittest

from recommendation import House, recommend


class RecommendationMatrixTests(unittest.TestCase):
    def check_case(self, houses, expected_ids, priority="균형", **limits):
        result = recommend(houses, priority, **limits)
        self.assertEqual(tuple(house.id for house in result.ranked), expected_ids)
        self.assertEqual(result, recommend(houses[::-1], priority, **limits))
        return result

    def test_dominance_in_all_three_dimensions(self):
        for offset in range(10):
            with self.subTest(offset=offset):
                houses = [
                    House("A", 445_000 + 83_000 * offset, 65_000, 19 + 7 * offset, 5, 7),
                    House("B", 502_000 + 83_000 * offset, 70_000, 24 + 7 * offset, 9, 11),
                    House("C", 550_000 + 83_000 * offset, 80_000, 30 + 7 * offset, 14, 18),
                ]
                for priority in ("균형", "시간이 먼저"):
                    self.check_case(houses, ("A", "B", "C"), priority)

    def test_limits_at_exact_value_and_one_unit_over(self):
        for offset in range(10):
            with self.subTest(offset=offset):
                budget = 675_000 + offset * 38_000
                commute = 28 + offset * 4
                houses = [
                    House("A", budget - 75_000, 75_000, commute, 11, 12),
                    House("B", budget - 61_999, 62_000, commute - 1, 6, 5),
                    House("C", budget - 74_000, 73_000, commute + 1, 8, 9),
                ]
                result = self.check_case(
                    houses, ("A",), "시간이 먼저",
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
                    House("A", unknown_rent, None, 27, None, 9),
                    House("B", budget - 86_000, 80_000, 41, 9, 10),
                    House("C", budget - 53_000, 56_000, 48, 8, 7),
                ]
                eligible = ("B",) if offset % 2 else ()
                result = self.check_case(houses, eligible, max_monthly_cost_won=budget)
                expected_exclusions = {"C": ("monthly_cost_won",)}
                if offset % 2:
                    expected_exclusions["A"] = ("monthly_cost_won",)
                self.assertEqual(result.excluded_fields, expected_exclusions)
                self.assertEqual(result.missing_fields, {
                    "A": ("maintenance_won", "supermarket_walk_minutes"),
                })

    def test_cost_and_commute_differences_above_reference_values(self):
        for offset in range(10):
            with self.subTest(offset=offset):
                cost = 1_036_000 + offset * 39_000
                commute = 63 + offset * 3
                houses = [
                    House("A", cost, 52_000, commute + 3, 8, 13),
                    House("B", cost + 120_000, 52_000, commute, 8, 13),
                ]
                for priority in ("균형", "시간이 먼저"):
                    result = self.check_case(houses, ("A", "B"), priority)
                    a, b = result.ranked
                    self.assertAlmostEqual(a.cost_score - b.cost_score, 12.0)
                    self.assertAlmostEqual(b.time_score - a.time_score, 5.0)

    def test_priority_switch_reports_round_trip_savings(self):
        for offset in range(10):
            with self.subTest(offset=offset):
                rent = 465_000 + offset * 19_000
                commute = 33 + offset
                days = 9 + offset * 2
                houses = [
                    House("A", rent, 48_000, commute, 13, 6),
                    House("B", rent + 176_000, 55_000, commute - 11, 13, 6),
                ]
                self.check_case(houses, ("A", "B"))
                result = self.check_case(houses, ("B", "A"), "시간이 먼저", commute_days=days)
                self.assertEqual(result.tradeoff.compared_house_id, "A")
                self.assertEqual(result.tradeoff.extra_monthly_cost_won, 183_000)
                self.assertEqual(result.tradeoff.monthly_hours_saved, round(22 * days / 60, 1))

    def test_fixed_priorities_against_personal_willingness_to_pay(self):
        # Each personal budget is for the stated time saving and is independent of the model.
        cases = [
            ("균형", 95_000, 110_000, "B", True),
            ("균형", 125_000, 100_000, "B", False),
            ("균형", 161_000, 150_000, "A", True),
            ("균형", 178_000, 195_000, "A", False),
            ("균형", 207_000, 170_000, "A", True),
            ("시간이 먼저", 144_000, 130_000, "B", False),
            ("시간이 먼저", 216_000, 225_000, "B", True),
            ("시간이 먼저", 279_000, 250_000, "B", False),
            ("시간이 먼저", 318_000, 300_000, "A", True),
            ("시간이 먼저", 342_000, 370_000, "A", False),
        ]
        for index, (priority, premium, willingness, model_winner, agrees) in enumerate(cases):
            with self.subTest(priority=priority, premium=premium, willingness=willingness):
                rent = 395_000 + index * 13_000
                commute = 29 + index
                houses = [
                    House("A", rent, 62_000, commute, 11, 14),
                    House("B", rent + premium, 62_000, commute - 10, 11, 14),
                ]
                other = "A" if model_winner == "B" else "B"
                self.check_case(houses, (model_winner, other), priority)
                personal_winner = "B" if premium <= willingness else "A"
                self.assertEqual(model_winner == personal_winner, agrees)
