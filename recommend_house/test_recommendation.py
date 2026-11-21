from dataclasses import replace
import unittest

from recommendation import House, recommend


class RecommendationTests(unittest.TestCase):
    def setUp(self):
        self.sillim = House("신림동", 400_000, 20_000, 30, 17, 6)
        self.seongsu = House("성수동", 550_000, 50_000, 30, 8, 8)
        self.yeoksam = House("역삼동", 850_000, 50_000, 13, 8, 15)
        self.houses = [self.sillim, self.seongsu, self.yeoksam]

    def test_balanced_ranking_and_subscores(self):
        result = recommend(self.houses)
        self.assertEqual([house.id for house in result.ranked], ["신림동", "성수동", "역삼동"])
        winner = result.ranked[0]
        self.assertEqual(winner.monthly_cost_won, 420_000)
        self.assertEqual(
            (winner.cost_score, winner.time_score, winner.convenience_score, winner.total_score),
            (58.0, 50.0, 61.7, 56.1),
        )
        self.assertIsNone(result.tradeoff)

    def test_time_priority_changes_winner_and_reports_tradeoff(self):
        result = recommend(self.houses, "시간이 먼저")
        self.assertEqual(result.ranked[0].id, "역삼동")
        self.assertIsNotNone(result.tradeoff)
        self.assertEqual(result.tradeoff.compared_house_id, "신림동")
        self.assertEqual(result.tradeoff.extra_monthly_cost_won, 480_000)
        self.assertEqual(result.tradeoff.monthly_hours_saved, 11.3)

    def test_tradeoff_uses_configured_commute_days(self):
        for days, expected_hours in [(0, 0.0), (15, 8.5), (31, 17.6)]:
            with self.subTest(days=days):
                result = recommend(self.houses, "시간이 먼저", commute_days=days)
                self.assertEqual(result.tradeoff.monthly_hours_saved, expected_hours)

    def test_time_priority_can_keep_same_winner(self):
        result = recommend([self.sillim, self.seongsu], "시간이 먼저")
        self.assertEqual(result.ranked[0].id, "신림동")
        self.assertIsNone(result.tradeoff)

    def test_time_priority_balances_extra_rent_and_minutes_saved(self):
        baseline = House("A", 400_000, 0, 40, 15, 15)
        cases = [
            (100_000, 10, "B"),
            (250_000, 10, "B"),
            (305_555, 10, "B"),
            (305_556, 10, "A"),
            (350_000, 10, "A"),
            (500_000, 10, "A"),
            (80_000, 2, "A"),
        ]
        for extra_rent, minutes_saved, expected in cases:
            with self.subTest(extra_rent=extra_rent, minutes_saved=minutes_saved):
                alternative = replace(
                    baseline,
                    id="B",
                    rent_won=baseline.rent_won + extra_rent,
                    commute_minutes=40 - minutes_saved,
                )
                result = recommend([baseline, alternative], "시간이 먼저")
                self.assertEqual(result.ranked[0].id, expected)

    def test_time_priority_still_considers_walkability(self):
        baseline = House("A", 400_000, 0, 40, 15, 15)
        for extra_rent, expected in [(50_000, "B"), (100_000, "A")]:
            with self.subTest(extra_rent=extra_rent):
                alternative = replace(
                    baseline,
                    id="B",
                    rent_won=baseline.rent_won + extra_rent,
                    supermarket_walk_minutes=5,
                )
                result = recommend([baseline, alternative], "시간이 먼저")
                self.assertEqual(result.ranked[0].id, expected)

    def test_adding_candidate_keeps_existing_scores(self):
        before = recommend(self.houses[:2])
        after = recommend(self.houses)
        self.assertEqual(before.ranked, after.ranked[:2])

    def test_incomplete_candidate_reports_all_missing_fields(self):
        incomplete = replace(self.yeoksam, maintenance_won=None, commute_minutes=None)
        result = recommend([self.sillim, self.seongsu, incomplete])
        self.assertEqual(result.missing_fields, {"역삼동": ("maintenance_won", "commute_minutes")})
        self.assertEqual([house.id for house in result.ranked], ["신림동", "성수동"])

    def test_single_complete_candidate_has_no_ranking(self):
        incomplete = replace(self.yeoksam, supermarket_walk_minutes=None)
        result = recommend([self.sillim, incomplete])
        self.assertEqual(result.ranked, ())
        self.assertEqual(result.missing_fields, {"역삼동": ("supermarket_walk_minutes",)})
        self.assertIsNone(result.tradeoff)

    def test_scores_keep_penalizing_cost_and_commute_beyond_reference(self):
        best = House("best", 0, 0, 0, 0, 0)
        reference = House("reference", 1_000_000, 0, 60, 30, 30)
        worst = House("worst", 1_500_000, 0, 90, 45, 45)
        result = recommend([worst, best, reference])
        self.assertEqual([house.total_score for house in result.ranked], [100.0, 0.0, -37.5])

    def test_ties_prefer_lower_cost_then_shorter_commute_then_id(self):
        houses = [
            House("a", 575_000, 0, 32, 12, 18),
            House("c", 400_000, 0, 44, 12, 18),
            House("b", 400_000, 0, 34, 19, 25),
        ]
        self.assertEqual([house.id for house in recommend(houses).ranked], ["b", "c", "a"])
        identical = [replace(self.sillim, id=key) for key in ("z", "a")]
        self.assertEqual([house.id for house in recommend(identical).ranked], ["a", "z"])

    def test_monthly_budget_includes_maintenance_and_allows_exact_limit(self):
        a = House("A", 720_000, 90_000, 38, 10, 10)
        b = House("B", 890_000, 90_000, 23, 10, 10)
        result = recommend([a, b], "시간이 먼저", max_monthly_cost_won=900_000)
        self.assertEqual([house.id for house in result.ranked], ["A"])
        self.assertEqual(result.excluded_fields, {"B": ("monthly_cost_won",)})
        self.assertEqual(len(recommend([a, b], max_monthly_cost_won=980_000).ranked), 2)

    def test_commute_limit_excludes_candidate_above_limit(self):
        a = House("A", 520_000, 80_000, 64, 9, 9)
        b = House("B", 450_000, 60_000, 79, 9, 9)
        result = recommend([a, b], "시간이 먼저", max_commute_minutes=65)
        self.assertEqual([house.id for house in result.ranked], ["A"])
        self.assertEqual(result.excluded_fields, {"B": ("commute_minutes",)})

    def test_high_rent_difference_affects_ranking(self):
        a = House("A", 1_000_000, 60_000, 28, 8, 8)
        b = House("B", 1_310_000, 70_000, 25, 8, 8)
        result = recommend([a, b])
        self.assertEqual([house.id for house in result.ranked], ["A", "B"])
        self.assertEqual([house.cost_score for house in result.ranked], [-6.0, -38.0])

    def test_all_candidates_can_be_excluded_and_multiple_reasons_are_reported(self):
        result = recommend(self.houses, max_monthly_cost_won=400_000, max_commute_minutes=20)
        self.assertEqual(result.ranked, ())
        self.assertIsNone(result.tradeoff)
        self.assertEqual(result.excluded_fields["신림동"], ("monthly_cost_won", "commute_minutes"))
        self.assertEqual(result.excluded_fields["역삼동"], ("monthly_cost_won",))

    def test_invalid_limits_are_rejected(self):
        for kwargs in ({"max_monthly_cost_won": -1}, {"max_commute_minutes": True}):
            with self.subTest(kwargs=kwargs), self.assertRaises(ValueError):
                recommend(self.houses, **kwargs)

    def test_invalid_candidates_are_rejected(self):
        cases = {
            "too few": [self.sillim],
            "too many": [*self.houses, replace(self.sillim, id="extra")],
            "duplicate id": [self.sillim, self.sillim],
            "blank id": [self.sillim, replace(self.yeoksam, id=" ")],
            "negative rent": [self.sillim, replace(self.yeoksam, rent_won=-1)],
            "negative commute": [self.sillim, replace(self.yeoksam, commute_minutes=-1)],
            "fractional walk time": [self.sillim, replace(self.yeoksam, subway_walk_minutes=3.5)],
        }
        for name, houses in cases.items():
            with self.subTest(name=name), self.assertRaises(ValueError):
                recommend(houses)

    def test_invalid_priority_and_commute_days_are_rejected(self):
        with self.assertRaises(ValueError):
            recommend(self.houses, "기타")
        for days in (-1, 32, 20.5, True):
            with self.subTest(days=days), self.assertRaises(ValueError):
                recommend(self.houses, commute_days=days)


if __name__ == "__main__":
    unittest.main()
