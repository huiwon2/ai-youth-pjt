package com.aiyouth.pjt.external;

import com.aiyouth.pjt.dto.response.PlanningResponse.Location;
import tools.jackson.databind.JsonNode;

public final class GeoDistance {
    private GeoDistance() {}

    public static Double coordinate(JsonNode value, int max) {
        if (value.isNumber() || value.isString()) {
            try {
                double coordinate = Double.parseDouble(value.asString());
                return Double.isFinite(coordinate) && Math.abs(coordinate) <= max ? coordinate : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static int straightLineMeters(Location start, Location end) {
        double a = Math.toRadians(start.latitude());
        double b = Math.toRadians(end.latitude());
        double halfLongitude = Math.toRadians(end.longitude() - start.longitude()) / 2;
        double haversine = Math.pow(Math.sin((b - a) / 2), 2) +
                Math.cos(a) * Math.cos(b) * Math.pow(Math.sin(halfLongitude), 2);
        return (int) Math.round(12_742_000 * Math.asin(Math.sqrt(Math.min(1.0, haversine))));
    }

    public static int walkSeconds(int straightLineMeters) {
        return (int) Math.ceil(straightLineMeters * 1.3 * 60 / 67);
    }
}
