from dataclasses import replace
import unittest

from recommendation import House, recommend


def home(id, rent, fee, commute, mart=8, store=8, pharmacy=8, *, deposit=0, support=0):
    return House(
        id, rent, fee, commute, mart,
        deposit_won=deposit,
        convenience_store_walk_minutes=store,
        pharmacy_walk_minutes=pharmacy,
        monthly_support_won=support,
    )


class RecommendationTests(unittest.TestCase):
    def setUp(self):
        self.sillim = home("신림동", 400_000, 20_000, 30, 17, 6, 10)
        self.seongsu = home("성수동", 550_000, 50_000, 30, 8, 8, 8)
        self.yeoksam = home("역삼동", 750_000, 50_000, 13, 8, 15, 8)
        self.houses = [self.sillim, self.seongsu, self.yeoksam]

    def test_balanced_ranking_and_subscores(self):
        result = recommend(self.houses)
        self.assertEqual([house.id for house in result.ranked], ["신림동", "성수동", "역삼동"])
        winner = result.ranked[0]
        self.assertEqual(winner.monthly_cost_won, 420_000)
        self.assertEqual(
            (winner.cost_score, winner.time_score, winner.convenience_score, winner.total_score),
            (58.0, 75.0, 87.0, 71.2),
        )
        self.assertIsNone(result.tradeoff)

    def test_commute_preset_changes_winner_and_reports_tradeoff(self):
        result = recommend(self.houses, "COMMUTE")
        self.assertEqual(result.ranked[0].id, "역삼동")
        self.assertEqual(result.tradeoff.compared_house_id, "신림동")
        self.assertEqual(result.tradeoff.extra_monthly_cost_won, 380_000)
        self.assertEqual(result.tradeoff.monthly_hours_saved, 11.3)

    def test_tradeoff_uses_configured_commute_days(self):
        for days, expected_hours in [(0, 0.0), (15, 8.5), (31, 17.6)]:
            with self.subTest(days=days):
                result = recommend(self.houses, "COMMUTE", commute_days=days)
                self.assertEqual(result.tradeoff.monthly_hours_saved, expected_hours)

    def test_commute_preset_can_keep_same_winner(self):
        result = recommend([self.sillim, self.seongsu], "COMMUTE")
        self.assertEqual(result.ranked[0].id, "신림동")
        self.assertIsNone(result.tradeoff)

    def test_custom_weights(self):
        result = recommend(self.houses, "CUSTOM", weights=(0, 100, 0))
        self.assertEqual(result.ranked[0].id, "역삼동")
        self.assertEqual(result.tradeoff.compared_house_id, "신림동")

    def test_implied_value_of_one_commute_minute(self):
        # BALANCED: 편도 1분 = 월 7,291.67원, COMMUTE: 편도 1분 = 월 25,000원. 동점이면 싼 집이 앞선다.
        baseline = home("A", 400_000, 0, 40)
        cases = [
            ("BALANCED", 7_291, "B"),
            ("BALANCED", 7_292, "A"),
            ("COMMUTE", 24_999, "B"),
            ("COMMUTE", 25_000, "A"),
        ]
        for priority, extra_rent, expected in cases:
            with self.subTest(priority=priority, extra_rent=extra_rent):
                faster = replace(baseline, id="B", rent_won=400_000 + extra_rent, commute_minutes=39)
                self.assertEqual(recommend([baseline, faster], priority).ranked[0].id, expected)

    def test_deposit_is_converted_to_monthly_cost(self):
        high_deposit = home("A", 300_000, 0, 30, deposit=100_000_000)
        low_deposit = home("B", 600_000, 0, 30, deposit=5_000_000)
        result = recommend([high_deposit, low_deposit])
        self.assertEqual([house.id for house in result.ranked], ["B", "A"])
        a = result.ranked[1]
        self.assertEqual(
            (a.monthly_cost_won, a.deposit_cost_won, a.effective_monthly_cost_won),
            (300_000, 375_000, 675_000),
        )
        ignored = recommend([high_deposit, low_deposit], deposit_annual_rate=0)
        self.assertEqual([house.id for house in ignored.ranked], ["A", "B"])

    def test_monthly_support_lowers_cost_and_budget_check(self):
        a = home("A", 500_000, 50_000, 30)
        b = home("B", 600_000, 50_000, 30, support=200_000)
        result = recommend([a, b])
        self.assertEqual([house.id for house in result.ranked], ["B", "A"])
        self.assertEqual(result.ranked[0].monthly_after_support_won, 450_000)
        limited = recommend([a, b], max_monthly_cost_won=460_000)
        self.assertEqual([house.id for house in limited.ranked], ["B"])
        self.assertEqual(limited.excluded_fields, {"A": ("monthly_cost_won",)})
        oversupported = recommend([a, replace(b, monthly_support_won=900_000)])
        self.assertEqual(oversupported.ranked[0].monthly_after_support_won, 0)

    def test_commute_days_do_not_change_ranking_or_cost(self):
        for days in (0, 10, 31):
            with self.subTest(days=days):
                result = recommend(self.houses, commute_days=days)
                self.assertEqual(result.ranked, recommend(self.houses).ranked)

    def test_adding_candidate_keeps_existing_scores(self):
        before = recommend(self.houses[:2])
        after = recommend(self.houses)
        self.assertEqual(before.ranked, after.ranked[:2])

    def test_missing_facility_uses_known_facilities(self):
        partial = replace(self.yeoksam, pharmacy_walk_minutes=None)
        result = recommend([self.sillim, partial])
        self.assertEqual([house.id for house in result.ranked], ["신림동", "역삼동"])
        self.assertEqual(result.missing_fields, {})
        self.assertEqual(result.estimated_fields, {"역삼동": ("pharmacy_walk_minutes",)})
        self.assertEqual(result.ranked[1].convenience_score, 84.8)

    def test_all_facilities_missing_uses_neutral_convenience(self):
        unknown = replace(
            self.yeoksam,
            supermarket_walk_minutes=None,
            convenience_store_walk_minutes=None,
            pharmacy_walk_minutes=None,
        )
        result = recommend([self.sillim, unknown])
        self.assertEqual(result.ranked[1].convenience_score, 50.0)

    def test_missing_cost_or_commute_blocks_ranking_for_that_house(self):
        incomplete = replace(self.yeoksam, maintenance_won=None, commute_minutes=None)
        result = recommend([self.sillim, self.seongsu, incomplete])
        self.assertEqual(result.missing_fields, {"역삼동": ("maintenance_won", "commute_minutes")})
        self.assertEqual([house.id for house in result.ranked], ["신림동", "성수동"])

    def test_single_complete_candidate_has_no_ranking(self):
        incomplete = replace(self.yeoksam, commute_minutes=None)
        result = recommend([self.sillim, incomplete])
        self.assertEqual(result.ranked, ())
        self.assertEqual(result.missing_fields, {"역삼동": ("commute_minutes",)})
        self.assertIsNone(result.tradeoff)

    def test_displayed_scores_are_clamped_but_ranking_keeps_penalizing(self):
        best = home("best", 0, 0, 0, 0, 0, 0)
        reference = home("reference", 1_000_000, 0, 120, 80, 80, 80)
        worst = home("worst", 1_500_000, 0, 180, 120, 120, 120)
        result = recommend([worst, best, reference])
        self.assertEqual([house.id for house in result.ranked], ["best", "reference", "worst"])
        self.assertEqual([house.total_score for house in result.ranked], [100.0, 0.0, 0.0])
        self.assertEqual((result.ranked[2].cost_score, result.ranked[2].time_score), (0.0, 0.0))

    def test_ties_prefer_lower_cost_then_shorter_commute_then_id(self):
        # 통근 3분 = 21,875원으로 정확히 동점.
        cheap_slow = home("b", 400_000, 0, 43)
        pricey_fast = home("a", 421_875, 0, 40)
        self.assertEqual([h.id for h in recommend([pricey_fast, cheap_slow]).ranked], ["b", "a"])
        # 통근 15분 = 모든 시설 도보 14분으로 정확히 동점.
        near_work = home("d", 400_000, 0, 40, 15, 15, 15)
        near_shops = home("c", 400_000, 0, 55, 1, 1, 1)
        self.assertEqual([h.id for h in recommend([near_shops, near_work]).ranked], ["d", "c"])
        identical = [replace(self.sillim, id=key) for key in ("z", "a")]
        self.assertEqual([h.id for h in recommend(identical).ranked], ["a", "z"])

    def test_monthly_budget_includes_maintenance_and_allows_exact_limit(self):
        a = home("A", 720_000, 90_000, 38, 10, 10, 10)
        b = home("B", 890_000, 90_000, 23, 10, 10, 10)
        result = recommend([a, b], "COMMUTE", max_monthly_cost_won=900_000)
        self.assertEqual([house.id for house in result.ranked], ["A"])
        self.assertEqual(result.excluded_fields, {"B": ("monthly_cost_won",)})
        self.assertEqual(len(recommend([a, b], max_monthly_cost_won=980_000).ranked), 2)

    def test_commute_limit_excludes_candidate_above_limit(self):
        a = home("A", 520_000, 80_000, 64, 9, 9, 9)
        b = home("B", 450_000, 60_000, 79, 9, 9, 9)
        result = recommend([a, b], "COMMUTE", max_commute_minutes=65)
        self.assertEqual([house.id for house in result.ranked], ["A"])
        self.assertEqual(result.excluded_fields, {"B": ("commute_minutes",)})

    def test_all_candidates_can_be_excluded_and_multiple_reasons_are_reported(self):
        result = recommend(self.houses, max_monthly_cost_won=400_000, max_commute_minutes=20)
        self.assertEqual(result.ranked, ())
        self.assertIsNone(result.tradeoff)
        self.assertEqual(result.excluded_fields["신림동"], ("monthly_cost_won", "commute_minutes"))
        self.assertEqual(result.excluded_fields["역삼동"], ("monthly_cost_won",))

    def test_legacy_positional_house_and_priority_names_still_work(self):
        # 이전 버전 호출: House(id, 월세, 관리비, 통근, 마트 도보, 지하철 도보)와 한글 우선순위 이름.
        legacy = [
            House("신림동", 400_000, 20_000, 30, 17, 6),
            House("성수동", 550_000, 50_000, 30, 8, 8),
            House("역삼동", 800_000, 50_000, 13, 8, 15),
        ]
        balanced = recommend(legacy, "균형")
        self.assertEqual(balanced, recommend(legacy, "BALANCED"))
        self.assertEqual(balanced, recommend(legacy))
        self.assertEqual(recommend(legacy, "시간이 먼저"), recommend(legacy, "COMMUTE"))
        winner = balanced.ranked[0]
        self.assertEqual(
            (winner.id, winner.monthly_cost_won, winner.effective_monthly_cost_won),
            ("신림동", 420_000, 420_000),
        )
        self.assertEqual(balanced.missing_fields, {})
        self.assertEqual(balanced.estimated_fields["신림동"], (
            "convenience_store_walk_minutes", "pharmacy_walk_minutes",
        ))

    def test_subway_walk_time_is_accepted_but_not_scored(self):
        far = replace(self.seongsu, subway_walk_minutes=25)
        near = replace(self.seongsu, subway_walk_minutes=1)
        self.assertEqual(recommend([self.sillim, far]).ranked, recommend([self.sillim, near]).ranked)
        with self.assertRaises(ValueError):
            recommend([self.sillim, replace(self.seongsu, subway_walk_minutes=-1)])

    def test_invalid_limits_and_rates_are_rejected(self):
        for kwargs in (
            {"max_monthly_cost_won": -1},
            {"max_commute_minutes": True},
            {"deposit_annual_rate": -0.01},
            {"deposit_annual_rate": 0.3},
            {"deposit_annual_rate": True},
            {"deposit_annual_rate": "0.05"},
        ):
            with self.subTest(kwargs=kwargs), self.assertRaises(ValueError):
                recommend(self.houses, **kwargs)

    def test_invalid_candidates_are_rejected(self):
        cases = {
            "too few": [self.sillim],
            "too many": [*self.houses, replace(self.sillim, id="extra")],
            "duplicate id": [self.sillim, self.sillim],
            "blank id": [self.sillim, replace(self.yeoksam, id=" ")],
            "non-string id": [self.sillim, replace(self.yeoksam, id=1)],
            "negative deposit": [self.sillim, replace(self.yeoksam, deposit_won=-1)],
            "negative rent": [self.sillim, replace(self.yeoksam, rent_won=-1)],
            "negative support": [self.sillim, replace(self.yeoksam, monthly_support_won=-1)],
            "negative commute": [self.sillim, replace(self.yeoksam, commute_minutes=-1)],
            "fractional walk time": [self.sillim, replace(self.yeoksam, pharmacy_walk_minutes=3.5)],
        }
        for name, houses in cases.items():
            with self.subTest(name=name), self.assertRaises(ValueError):
                recommend(houses)

    def test_invalid_priority_weights_and_commute_days_are_rejected(self):
        for priority, kwargs in (
            ("기타", {}),
            ("균형", {"weights": (40, 35, 25)}),
            ("CUSTOM", {}),
            ("CUSTOM", {"weights": (40, 35, 24)}),
            ("CUSTOM", {"weights": (101, -1, 0)}),
            ("CUSTOM", {"weights": (50, 50)}),
            ("BALANCED", {"weights": (40, 35, 25)}),
        ):
            with self.subTest(priority=priority, kwargs=kwargs), self.assertRaises(ValueError):
                recommend(self.houses, priority, **kwargs)
        for days in (-1, 32, 20.5, True):
            with self.subTest(days=days), self.assertRaises(ValueError):
                recommend(self.houses, commute_days=days)


if __name__ == "__main__":
    unittest.main()
