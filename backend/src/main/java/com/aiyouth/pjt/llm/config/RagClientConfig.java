package com.aiyouth.pjt.llm.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(RagServiceProperties.class)
public class RagClientConfig {

    @Bean
    RestClient ragRestClient(RagServiceProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.timeout());
        factory.setReadTimeout(props.timeout());
        // 본문을 버퍼링해 Content-Length를 붙인다. 기본(청크 전송)은 Flask 개발 서버 등 일부 Python 서버가 읽지 못한다.
        RestClient.Builder builder = RestClient.builder().requestFactory(new BufferingClientHttpRequestFactory(factory));
        if (props.baseUrl() != null && !props.baseUrl().isBlank()) {
            builder.baseUrl(props.baseUrl());
        }
        return builder.build();
    }
}
