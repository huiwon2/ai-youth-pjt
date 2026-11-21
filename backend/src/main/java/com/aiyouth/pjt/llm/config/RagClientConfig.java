package com.aiyouth.pjt.llm.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(RagServiceProperties.class)
public class RagClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    @Bean
    RestClient ragRestClient(RagServiceProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 서버가 꺼져 있으면 응답 한도(15초)까지 기다리지 않도록 연결 대기는 짧게 둔다
        factory.setConnectTimeout(props.timeout().compareTo(CONNECT_TIMEOUT) < 0 ? props.timeout() : CONNECT_TIMEOUT);
        factory.setReadTimeout(props.timeout());
        // 본문을 버퍼링해 Content-Length를 붙인다. 기본(청크 전송)은 Flask 개발 서버 등 일부 Python 서버가 읽지 못한다.
        RestClient.Builder builder = RestClient.builder().requestFactory(new BufferingClientHttpRequestFactory(factory));
        if (props.baseUrl() != null && !props.baseUrl().isBlank()) {
            builder.baseUrl(props.baseUrl());
        }
        return builder.build();
    }
}
