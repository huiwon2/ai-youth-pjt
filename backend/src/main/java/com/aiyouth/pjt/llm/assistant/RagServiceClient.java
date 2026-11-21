package com.aiyouth.pjt.llm.assistant;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.aiyouth.pjt.llm.config.RagServiceProperties;

/**
 * Python RAG 서비스 호출. 계약은 backend/docs/rag-service-contract.md.
 * 연결 실패·시간 초과·5xx·형식 오류는 모두 Optional.empty()로 돌려 호출부가 원문 발췌로 대체하게 한다.
 */
@Component
public class RagServiceClient {

    private static final Logger log = LoggerFactory.getLogger(RagServiceClient.class);

    private final RestClient client;
    private final RagServiceProperties props;

    public RagServiceClient(@Qualifier("ragRestClient") RestClient client, RagServiceProperties props) {
        this.client = client;
        this.props = props;
    }

    public boolean isEnabled() {
        return props.enabled() && props.baseUrl() != null && !props.baseUrl().isBlank();
    }

    public Optional<RagAnswer> answerPolicyQuestion(RagQuestion question) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        try {
            RagAnswer answer = client.post()
                    .uri("/v1/policy-answers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(question)
                    .retrieve()
                    .body(RagAnswer.class);
            return Optional.ofNullable(answer).filter(a -> a.status() != null && a.answer() != null);
        } catch (RestClientException e) {
            log.warn("RAG 서비스 호출 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Java → Python 요청. 질문 원문과 선택 정책만 보낸다 (사용자 프로필은 보내지 않는다). */
    public record RagQuestion(String question, String policyKey, LocalDate assessmentDate) {
    }

    /**
     * Python → Java 응답.
     *
     * @param status      ANSWERED / NO_EVIDENCE / OUT_OF_SCOPE
     * @param evidenceIds 답변에 쓴 rag_chunks.id. Java가 DB에서 VERIFIED 여부를 다시 확인한다
     */
    public record RagAnswer(String status, String answer, List<String> evidenceIds, List<HelpLink> helpLinks) {

        public record HelpLink(String label, String url) {
        }
    }
}
