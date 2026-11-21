package com.aiyouth.pjt.service.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.aiyouth.pjt.dto.request.policy.PolicyQuestionRequest;
import com.aiyouth.pjt.dto.response.PlanningResponse.Session;
import com.aiyouth.pjt.dto.response.policy.Evidence;
import com.aiyouth.pjt.dto.response.policy.PolicyAnswerResponse;
import com.aiyouth.pjt.dto.response.policy.PolicyAnswerResponse.HelpLink;
import com.aiyouth.pjt.llm.assistant.RagServiceClient;
import com.aiyouth.pjt.llm.assistant.RagServiceClient.RagAnswer;
import com.aiyouth.pjt.llm.assistant.RagServiceClient.RagQuestion;
import com.aiyouth.pjt.llm.guard.AnswerNumberGuard;
import com.aiyouth.pjt.llm.guard.AnswerText;
import com.aiyouth.pjt.llm.retriever.SourceExcerptRetriever;
import com.aiyouth.pjt.mapper.PolicyMapper;
import com.aiyouth.pjt.model.Policy;
import com.aiyouth.pjt.model.RagChunk;
import com.aiyouth.pjt.model.enums.AnswerSource;
import com.aiyouth.pjt.model.enums.AnswerStatus;
import com.aiyouth.pjt.service.PlanningSessions;

/**
 * API 6 공고문 질문.
 * <ol>
 *   <li>Node.js RAG 서비스가 답과 근거 ID를 돌려주면, Java가 근거의 VERIFIED 여부와 답변 숫자를 확인한다.</li>
 *   <li>숫자가 근거에 없으면 AI 문장 대신 원문 발췌(SOURCE_EXCERPT)로 답한다.</li>
 *   <li>RAG 서버를 쓸 수 없으면 키워드로 찾은 원문 발췌를 FALLBACK으로 보여주고, 그마저 없으면 근거 없음을 알린다.</li>
 * </ol>
 * 세션 소유권(작업 쿠키)·만료·입력 버전은 {@link PlanningSessions#getForInput}이 확인한다.
 * 등록된 시연 대체 답변(PREPARED_ANSWER)은 승인된 질문·답변 문안이 준비되면 추가한다.
 */
@Service
public class PolicyQuestionService {

    static final String MSG_NO_EVIDENCE = "공고문에서 확인된 근거를 찾지 못했어요. 담당 기관에 문의하거나 원문을 확인해 주세요.";
    static final String MSG_EXCERPT = "AI 답변의 숫자를 근거에서 확인하지 못해 공고문 원문을 그대로 보여드려요.";
    static final String MSG_FALLBACK_EXCERPT = "AI 답변 서버에 연결하지 못해 관련 공고문 원문을 보여드려요. 아래 근거에서 직접 확인해 주세요.";
    static final String MSG_FALLBACK_EMPTY = "AI 답변 서버에 연결하지 못했고, 관련 공고문 원문도 찾지 못했어요. 잠시 후 다시 시도하거나 담당 기관에 문의해 주세요.";

    private final PlanningSessions sessions;
    private final RagServiceClient rag;
    private final SourceExcerptRetriever retriever;
    private final PolicyMapper policyMapper;

    public PolicyQuestionService(PlanningSessions sessions, RagServiceClient rag, SourceExcerptRetriever retriever,
                                 PolicyMapper policyMapper) {
        this.sessions = sessions;
        this.rag = rag;
        this.retriever = retriever;
        this.policyMapper = policyMapper;
    }

    public PolicyAnswerResponse ask(UUID sessionId, String token, PolicyQuestionRequest request) {
        Session ctx = sessions.getForInput(sessionId, token, request.inputRevision());
        String question = request.question().strip();
        String policyKey = request.policyKey() == null || request.policyKey().isBlank() ? null : request.policyKey();
        List<HelpLink> policyLinks = policyLinks(policyKey);

        Optional<RagAnswer> ai = rag.answerPolicyQuestion(new RagQuestion(question, policyKey, ctx.assessmentDate()));
        if (ai.isPresent()) {
            return fromAi(ctx, ai.get(), policyLinks);
        }
        List<RagChunk> excerpts = retriever.searchByKeywords(question, policyKey);
        if (excerpts.isEmpty()) {
            return answer(ctx, AnswerStatus.FALLBACK, MSG_FALLBACK_EMPTY, null, List.of(), policyLinks);
        }
        return answer(ctx, AnswerStatus.FALLBACK, MSG_FALLBACK_EXCERPT, AnswerSource.SOURCE_EXCERPT, excerpts,
                policyLinks);
    }

    private PolicyAnswerResponse fromAi(Session ctx, RagAnswer ai, List<HelpLink> policyLinks) {
        List<HelpLink> links = new ArrayList<>(policyLinks);
        if (ai.helpLinks() != null) {
            ai.helpLinks().stream().filter(l -> l.url() != null).forEach(l -> links.add(new HelpLink(l.label(), l.url())));
        }
        AnswerStatus status = parseStatus(ai.status());
        if (status == AnswerStatus.OUT_OF_SCOPE) {
            return answer(ctx, AnswerStatus.OUT_OF_SCOPE, AnswerText.toPlain(ai.answer()), null, List.of(), links);
        }
        List<RagChunk> chunks = retriever.verifiedByIds(ai.evidenceIds());
        if ("EXCERPT".equals(ai.status()) && !chunks.isEmpty()) {
            String excerpt = chunks.stream().anyMatch(c -> c.getContent().contains(ai.answer()))
                    ? AnswerText.toPlain(ai.answer()) : "관련 공고문 원문을 확인해 주세요.";
            return answer(ctx, AnswerStatus.ANSWERED, excerpt, AnswerSource.SOURCE_EXCERPT, chunks, links);
        }
        if (status != AnswerStatus.ANSWERED || chunks.isEmpty()) {
            // 검증된 근거 없이 '답변함'이라고 한 경우도 근거 없음으로 본다
            return answer(ctx, AnswerStatus.NO_EVIDENCE, MSG_NO_EVIDENCE, null, List.of(), links);
        }
        List<String> sources = chunks.stream().map(RagChunk::getContent).toList();
        if (!AnswerNumberGuard.numbersSupported(ai.answer(), sources)) {
            return answer(ctx, AnswerStatus.ANSWERED, MSG_EXCERPT, AnswerSource.SOURCE_EXCERPT, chunks, links);
        }
        // 숫자 검증은 원래 답변으로 하고, 화면에는 평문으로 보낸다 (프론트는 문자열을 그대로 출력)
        return answer(ctx, AnswerStatus.ANSWERED, AnswerText.toPlain(ai.answer()), AnswerSource.GROUNDED, chunks,
                links);
    }

    private List<HelpLink> policyLinks(String policyKey) {
        if (policyKey == null) {
            return List.of();
        }
        Policy policy = policyMapper.findByPolicyKey(policyKey);
        if (policy == null || policy.getSourceUrl() == null) {
            return List.of();
        }
        return List.of(new HelpLink(policy.getName() + " 원문", policy.getSourceUrl()));
    }

    private static AnswerStatus parseStatus(String raw) {
        try {
            return AnswerStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return AnswerStatus.NO_EVIDENCE;
        }
    }

    private static PolicyAnswerResponse answer(Session ctx, AnswerStatus status, String text,
                                               AnswerSource source, List<RagChunk> chunks, List<HelpLink> links) {
        return new PolicyAnswerResponse(status, text, source, chunks.stream().map(Evidence::from).toList(), links,
                ctx.revision(), ctx.assessmentDate());
    }
}
