package com.aiyouth.pjt.llm.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Node.js RAG 서비스 연결 설정 (프롬프트·임베딩·검색·LLM 호출은 Node.js 쪽).
 *
 * @param enabled  false면 호출하지 않고 DB 원문 발췌로 대체한다
 * @param baseUrl  예: http://localhost:8000
 * @param timeout  응답 대기 한도(기본 15초, 프론트 로딩 기준과 같음). 넘으면 원문 발췌로 대체한다
 */
@ConfigurationProperties(prefix = "firsthome.rag")
public record RagServiceProperties(boolean enabled, String baseUrl, Duration timeout) {

    public RagServiceProperties {
        if (timeout == null) {
            timeout = Duration.ofSeconds(15);
        }
    }
}
