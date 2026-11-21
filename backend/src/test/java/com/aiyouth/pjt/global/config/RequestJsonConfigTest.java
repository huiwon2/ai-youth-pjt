package com.aiyouth.pjt.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiyouth.pjt.global.exception.ApiErrors;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringJUnitWebConfig(classes = {RequestJsonConfigTest.WebConfig.class, RequestJsonConfig.class,
        ApiErrors.class, RequestJsonConfigTest.TestController.class})
class RequestJsonConfigTest {
    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void unknownFieldUsesTheApiValidationError() throws Exception {
        mvc.perform(post("/api/v1/test").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputRevision\":4,\"unexpected\":true}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("FIELD_INVALID"))
                .andExpect(jsonPath("$.error.fields[0].field").value("unexpected"))
                .andExpect(jsonPath("$.error.fields[0].code").value("UNKNOWN_FIELD"));
    }

    @Test
    void nestedUnknownFieldIncludesItsRequestPath() throws Exception {
        mvc.perform(post("/api/v1/test").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputRevision\":4,\"overrides\":{\"ageYear\":35}}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.fields[0].field").value("overrides.ageYear"))
                .andExpect(jsonPath("$.error.fields[0].code").value("UNKNOWN_FIELD"));
    }

    @Test
    void jsonIgnoredValidatorIsAlsoRejectedAsInput() throws Exception {
        mvc.perform(post("/api/v1/test").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputRevision\":4,\"valid\":true}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.fields[0].field").value("valid"))
                .andExpect(jsonPath("$.error.fields[0].code").value("UNKNOWN_FIELD"));
    }

    @Test
    void malformedJsonStillUsesTheSyntaxError() throws Exception {
        mvc.perform(post("/api/v1/test").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputRevision\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_JSON"));
    }

    @Configuration
    @EnableWebMvc
    static class WebConfig {
        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }

    @RestController
    static class TestController {
        @PostMapping("/api/v1/test")
        TestRequest accept(@RequestBody TestRequest request) {
            return request;
        }
    }

    record TestRequest(int inputRevision, Overrides overrides) {
        @JsonIgnore
        public boolean isValid() {
            return inputRevision >= 0;
        }
    }

    record Overrides(Integer ageYears) {}
}
