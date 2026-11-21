package com.aiyouth.pjt.external;

import com.aiyouth.pjt.global.exception.ApiException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProviderHttp {
    private final HttpClient client;
    private final ObjectMapper json;

    public ProviderHttp(HttpClient client, ObjectMapper json) {
        this.client = client;
        this.json = json;
    }

    public JsonNode get(String endpoint, Map<String, ?> params, Map<String, String> headers) {
        String query = params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue().toString()))
                .collect(Collectors.joining("&"));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint + "?" + query))
                .timeout(Duration.ofSeconds(3)).header("Accept", "application/json");
        headers.forEach(builder::header);
        var responseFuture = client.sendAsync(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
        try {
            var response = responseFuture.get(3, TimeUnit.SECONDS);
            return switch (response.statusCode()) {
                case 200 -> json.readTree(response.body());
                case 429 -> throw new ApiException(429, "RATE_LIMITED", "외부 조회 요청이 많아요. 잠시 뒤 다시 시도해 주세요.", true);
                case 504 -> throw timeout();
                default -> throw unavailable();
            };
        } catch (TimeoutException error) {
            responseFuture.cancel(true);
            throw timeout();
        } catch (InterruptedException error) {
            responseFuture.cancel(true);
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (ExecutionException error) {
            if (error.getCause() instanceof java.net.http.HttpTimeoutException) {
                throw timeout();
            }
            throw unavailable();
        } catch (JacksonException error) {
            throw unavailable();
        }
    }

    public static ApiException unavailable() {
        return new ApiException(503, "UPSTREAM_UNAVAILABLE", "외부 조회를 사용할 수 없어요. 잠시 뒤 다시 시도해 주세요.", true);
    }

    private static ApiException timeout() {
        return new ApiException(504, "UPSTREAM_TIMEOUT", "외부 조회 응답이 늦어요. 다시 시도해 주세요.", true);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
