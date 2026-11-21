package com.aiyouth.pjt.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RequestJsonConfig implements WebMvcConfigurer {
    private final JsonMapper json;

    public RequestJsonConfig(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.withJsonConverter(new JacksonJsonHttpMessageConverter(json) {
            @Override
            protected ObjectReader customizeReader(ObjectReader reader, JavaType type) {
                return reader.with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);
            }
        });
    }
}
