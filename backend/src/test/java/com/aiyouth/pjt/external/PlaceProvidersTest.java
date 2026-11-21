package com.aiyouth.pjt.external;

import static org.junit.jupiter.api.Assertions.*;

import com.aiyouth.pjt.dto.response.PlanningResponse.Location;
import com.aiyouth.pjt.external.kakao.KakaoLocal;
import com.aiyouth.pjt.external.odsay.OdsayTransit;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PlaceProvidersTest {
    private static final Location HOME = new Location("집", "집 주소", 127.05, 37.54);
    private static final Location DESTINATION = new Location("학교", "학교 주소", 127.04, 37.50);
    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

    private HttpServer server;
    private ProviderHttp http;
    private String endpoint;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
        http = new ProviderHttp(HttpClient.newHttpClient(), new ObjectMapper());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void buildingNumberIsRequiredForHomeEvenWhenKakaoSuggestsABuildingForRoadName() {
        respond("/v2/local/search/address.json", """
                {"documents":[{"address_name":"서울 강남구 테헤란로 212","x":"127.04","y":"37.50",
                  "road_address":{"address_name":"서울 강남구 테헤란로 212","main_building_no":"212"}}]}
                """);
        KakaoLocal local = new KakaoLocal(http, "test-key", endpoint);
        assertTrue(local.search("HOME", "테헤란로 212").get(0).selectable());
        var street = local.search("HOME", "테헤란로").get(0);
        assertFalse(street.selectable());
        assertEquals("BUILDING_NUMBER_REQUIRED", street.reason());
    }

    @Test
    void odsayResponsePreservesFareAndTravelSegments() {
        respond("/v1/api/searchPubTransPathT", """
                {"result":{"path":[{"info":{"totalTime":30,"totalDistance":7750.3,"payment":1550},
                  "subPath":[{"trafficType":3,"sectionTime":5},
                             {"trafficType":1,"sectionTime":25,"lane":[{"name":"2호선"}]}]}]}}
                """);
        var route = new OdsayTransit(http, "test-key", endpoint).route(HOME, DESTINATION, NOW);
        assertEquals("OK", route.status());
        assertEquals(1800, route.durationSeconds());
        assertEquals(1550, route.oneWayFareWon());
        assertEquals(7750, route.distanceMeters());
        assertEquals("WALK", route.segments().get(0).mode());
        assertEquals("2호선", route.segments().get(1).lineName());
    }

    @Test
    void shortDistanceFallbackExposesOnlyAnEstimatedRouteAndNoConfirmedFare() {
        respond("/v1/api/searchPubTransPathT", """
                {"error":{"code":"-98"}}
                """);
        Location near = new Location("근처", "근처 주소", 127.05, 37.541);
        var route = new OdsayTransit(http, "test-key", endpoint).route(HOME, near, NOW);
        assertEquals("ESTIMATED", route.status());
        assertEquals("WALK", route.mode());
        assertEquals("DISTANCE_ESTIMATE", route.source());
        assertNull(route.oneWayFareWon());
        assertEquals(2, route.geometry().coordinates().size());
    }

    private void respond(String path, String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
    }
}
