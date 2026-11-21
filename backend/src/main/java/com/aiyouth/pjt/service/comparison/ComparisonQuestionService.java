package com.aiyouth.pjt.service.comparison;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.aiyouth.pjt.calculator.KoreanText;
import com.aiyouth.pjt.dto.response.PlanningResponse.Session;
import com.aiyouth.pjt.dto.response.comparison.ComparisonAnswerResponse;
import com.aiyouth.pjt.dto.response.comparison.ComparisonAnswerResponse.Reference;
import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse;
import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse.ComparisonHome;
import com.aiyouth.pjt.global.exception.ApiException;
import com.aiyouth.pjt.model.ComparisonRun;
import com.aiyouth.pjt.model.enums.AnswerSource;
import com.aiyouth.pjt.model.enums.AnswerStatus;
import com.aiyouth.pjt.service.PlanningSessions;

import tools.jackson.databind.json.JsonMapper;

/**
 * API 12 비교 후속 질문. 저장된 비교 결과의 필드와 단위로만 답한다 (answerSource = RULE).
 * 세션 소유권(작업 쿠키)·만료·입력 버전은 {@link PlanningSessions#getForInput}이 확인한다.
 * AI 설명을 붙이더라도 숫자·결론이 이 결과와 다르면 이 코드 답변을 쓴다.
 */
@Service
public class ComparisonQuestionService {

    private static final Set<String> POLICY_WORDS = Set.of("신청", "공고", "지원금", "자격", "접수", "마감", "서류");
    private static final Set<String> UNSUPPORTED_WORDS = Set.of("소음", "채광", "층간", "치안", "보안", "곰팡이",
            "수압", "햇빛", "벌레");
    private static final Set<String> RECOMMEND_WORDS = Set.of("추천", "왜", "1위", "순위", "어디가", "어느");
    private static final Set<String> GENERIC_LABEL_WORDS = Set.of("원룸", "투룸", "쓰리룸", "오피스텔", "아파트", "빌라",
            "주택", "집", "후보");

    private final PlanningSessions sessions;
    private final ComparisonService comparisonService;
    private final JsonMapper json;

    public ComparisonQuestionService(PlanningSessions sessions, ComparisonService comparisonService,
                                     JsonMapper json) {
        this.sessions = sessions;
        this.comparisonService = comparisonService;
        this.json = json;
    }

    public ComparisonAnswerResponse ask(UUID sessionId, String token, UUID comparisonId, int inputRevision,
                                        String question) {
        Session session = sessions.getForInput(sessionId, token, inputRevision);
        ComparisonRun run = comparisonService.requireRun(sessionId, comparisonId);
        if (!comparisonService.isCurrent(run, session.revision(), session.assessmentDate())) {
            throw new ApiException(409, "STALE_COMPARISON", "입력이 바뀌어 이 비교 결과가 오래됐어요. 다시 비교해 주세요.",
                    false);
        }
        ComparisonResponse comparison = json.readValue(run.getResultSnapshot(), ComparisonResponse.class);
        return answer(comparison, question.strip());
    }

    static ComparisonAnswerResponse answer(ComparisonResponse comparison, String question) {
        if (containsAny(question, POLICY_WORDS)) {
            return reply(comparison, AnswerStatus.OUT_OF_SCOPE,
                    "신청 시기나 자격은 '받을 지원' 화면의 공고문 질문에서 확인할 수 있어요.", null, List.of());
        }
        if (containsAny(question, UNSUPPORTED_WORDS)) {
            return reply(comparison, AnswerStatus.OUT_OF_SCOPE,
                    "이 비교에는 그 정보가 없어요. 집을 직접 보거나 중개사에게 확인해 주세요.", null, List.of());
        }
        List<ComparisonHome> mentioned = mentionedHomes(comparison.homes(), question);
        if (mentioned.size() >= 2) {
            return difference(comparison, mentioned.get(0), mentioned.get(1));
        }
        if (containsAny(question, RECOMMEND_WORDS)) {
            return recommendation(comparison);
        }
        if (mentioned.size() == 1) {
            return single(comparison, mentioned.get(0));
        }
        return reply(comparison, AnswerStatus.NO_EVIDENCE,
                "비교 결과에서 답할 수 있는 내용을 찾지 못했어요. 집 이름과 함께 비용이나 이동 시간을 물어봐 주세요.", null,
                List.of());
    }

    private static ComparisonAnswerResponse difference(ComparisonResponse c, ComparisonHome a, ComparisonHome b) {
        List<String> sentences = new ArrayList<>();
        List<Reference> refs = new ArrayList<>(costRefs(a));
        refs.addAll(costRefs(b));
        if (a.cost().excludedUnknownMaintenance() || b.cost().excludedUnknownMaintenance()) {
            ComparisonHome unknown = a.cost().excludedUnknownMaintenance() ? a : b;
            sentences.add(KoreanText.topic(unknown.label()) + " 관리비를 몰라 한 달 비용을 같은 기준으로 비교할 수 없어요.");
        } else {
            long diff = a.cost().monthlyHousingCostWon() - b.cost().monthlyHousingCostWon();
            if (diff == 0) {
                sentences.add("두 집의 한 달 비용은 " + KoreanText.won(a.cost().monthlyHousingCostWon()) + "으로 같아요.");
            } else {
                ComparisonHome cheaper = diff < 0 ? a : b;
                ComparisonHome other = diff < 0 ? b : a;
                sentences.add(KoreanText.topic(cheaper.label()) + " " + other.label() + "보다 한 달 "
                        + KoreanText.won(Math.abs(diff)) + " 적게 들어요.");
            }
        }
        if (ComparisonService.hasDuration(a.route()) && ComparisonService.hasDuration(b.route())) {
            refs.add(new Reference(a.homeId(), "route.durationSeconds", a.route().durationSeconds()));
            refs.add(new Reference(b.homeId(), "route.durationSeconds", b.route().durationSeconds()));
            int diff = KoreanText.minutes(a.route().durationSeconds()) - KoreanText.minutes(b.route().durationSeconds());
            if (diff == 0) {
                sentences.add("편도 이동 시간은 같아요.");
            } else {
                ComparisonHome faster = diff < 0 ? a : b;
                sentences.add(KoreanText.topic(faster.label()) + " 편도 " + Math.abs(diff) + "분 짧아요.");
            }
        } else {
            sentences.add("이동 시간은 확인하지 못한 집이 있어 비교할 수 없어요.");
        }
        return reply(c, AnswerStatus.ANSWERED, String.join(" ", sentences), AnswerSource.RULE, refs);
    }

    private static ComparisonAnswerResponse single(ComparisonResponse c, ComparisonHome h) {
        List<Reference> refs = new ArrayList<>(costRefs(h));
        StringBuilder text = new StringBuilder(KoreanText.topic(h.label())).append(" 한 달 ")
                .append(KoreanText.won(h.cost().monthlyHousingCostWon()));
        text.append(h.cost().excludedUnknownMaintenance() ? "(관리비 별도)이에요." : "이에요.");
        if (ComparisonService.hasDuration(h.route())) {
            refs.add(new Reference(h.homeId(), "route.durationSeconds", h.route().durationSeconds()));
            text.append(" 편도 ").append(KoreanText.minutes(h.route().durationSeconds())).append("분 걸려요.");
        }
        return reply(c, AnswerStatus.ANSWERED, text.toString(), AnswerSource.RULE, refs);
    }

    private static ComparisonAnswerResponse recommendation(ComparisonResponse c) {
        UUID topId = c.recommendation().homeId();
        Optional<ComparisonHome> top = c.homes().stream().filter(h -> h.homeId().equals(topId)).findFirst();
        if (top.isEmpty()) {
            String reason = c.recommendation().tradeoffs().isEmpty()
                    ? "아직 순위를 정할 근거가 부족해 추천을 보류했어요."
                    : "추천을 보류했어요. " + String.join(" ", c.recommendation().tradeoffs());
            return reply(c, AnswerStatus.ANSWERED, reason, AnswerSource.RULE, List.of());
        }
        ComparisonHome h = top.get();
        List<Reference> refs = new ArrayList<>(costRefs(h));
        refs.add(new Reference(h.homeId(), "rank", h.rank()));
        refs.add(new Reference(h.homeId(), "scores.total", h.scores().total()));
        String text = KoreanText.topic(h.label()) + " " + h.rank() + "위예요. "
                + String.join(" ", c.recommendation().reasons())
                + (c.recommendation().tradeoffs().isEmpty() ? ""
                : " 다만 " + String.join(" ", c.recommendation().tradeoffs()));
        return reply(c, AnswerStatus.ANSWERED, text.strip(), AnswerSource.RULE, refs);
    }

    private static List<Reference> costRefs(ComparisonHome h) {
        return List.of(new Reference(h.homeId(), "cost.monthlyHousingCostWon", h.cost().monthlyHousingCostWon()),
                new Reference(h.homeId(), "cost.excludedUnknownMaintenance", h.cost().excludedUnknownMaintenance()));
    }

    /** 질문에 나온 순서대로, 라벨(또는 '성수동'→'성수' 같은 줄임)이 포함된 집을 찾는다. */
    static List<ComparisonHome> mentionedHomes(List<ComparisonHome> homes, String question) {
        record Hit(int index, ComparisonHome home) {
        }
        List<Hit> hits = new ArrayList<>();
        for (ComparisonHome home : homes) {
            int best = -1;
            for (String token : labelTokens(home.label())) {
                int idx = question.indexOf(token);
                if (idx >= 0 && (best < 0 || idx < best)) {
                    best = idx;
                }
            }
            if (best >= 0) {
                hits.add(new Hit(best, home));
            }
        }
        return hits.stream().sorted((x, y) -> Integer.compare(x.index(), y.index())).map(Hit::home).toList();
    }

    /** 동 이름 뒤 번지·가 표기: 성수동2가 → 성수동, 역삼동 → 역삼동 */
    private static final java.util.regex.Pattern DONG = java.util.regex.Pattern.compile("^(\\p{L}{2,}?)동(\\d+가)?$");

    /**
     * 라벨에서 질문과 맞춰 볼 이름을 뽑는다. 라벨이 주소("서울 성동구 성수동2가 333-76")여도
     * 동 이름(성수동·성수)을 찾는다. 시·구·번지와 원룸 같은 일반어는 집을 구별하지 못하므로 뺀다.
     */
    static List<String> labelTokens(String label) {
        List<String> tokens = new ArrayList<>();
        for (String word : label.split("\\s+")) {
            if (word.length() < 2 || GENERIC_LABEL_WORDS.contains(word) || word.chars().anyMatch(Character::isDigit)
                    && !DONG.matcher(word).matches()) {
                continue;
            }
            if (word.endsWith("시") || word.endsWith("구") || word.equals("서울")) {
                continue;
            }
            java.util.regex.Matcher dong = DONG.matcher(word);
            if (dong.matches()) {
                tokens.add(dong.group(1) + "동");
                tokens.add(dong.group(1));
            } else {
                tokens.add(word);
            }
        }
        return tokens;
    }

    private static boolean containsAny(String text, Set<String> words) {
        return words.stream().anyMatch(text::contains);
    }

    private static ComparisonAnswerResponse reply(ComparisonResponse c, AnswerStatus status, String answer,
                                                  AnswerSource source, List<Reference> refs) {
        return new ComparisonAnswerResponse(status, answer, source, refs, c.comparisonId(), c.inputRevision());
    }
}
