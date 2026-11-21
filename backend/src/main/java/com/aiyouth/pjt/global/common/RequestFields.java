package com.aiyouth.pjt.global.common;

import com.aiyouth.pjt.global.exception.ApiException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class RequestFields {
    private final JsonNode object;
    private final String prefix;

    public RequestFields(JsonNode object, String... fields) {
        this(object, "", fields);
    }

    private RequestFields(JsonNode object, String prefix, String[] fields) {
        this.object = object;
        this.prefix = prefix;
        if (object == null || !object.isObject()) {
            throw ApiException.invalid(prefix.isEmpty() ? "body" : prefix.substring(0, prefix.length() - 1), "OBJECT");
        }
        Set<String> allowed = Set.of(fields);
        for (String property : object.propertyNames()) {
            if (!allowed.contains(property)) {
                throw invalid(property, "UNKNOWN_FIELD");
            }
        }
    }

    public boolean has(String name) {
        return object.has(name);
    }

    public boolean blank(String name) {
        return object.path(name).isNull() || !has(name);
    }

    public boolean isEmpty() {
        return object.isEmpty();
    }

    public RequestFields nested(String name, String... fields) {
        return new RequestFields(object.get(name), prefix + name + ".", fields);
    }

    public String text(String name, int maxLength) {
        JsonNode value = required(name);
        if (!value.isString() || value.asString().isBlank() || value.asString().length() > maxLength
                || value.asString().indexOf(0) >= 0) {
            throw invalid(name, "FORMAT");
        }
        return value.asString().strip();
    }

    public String oneOf(String name, String... values) {
        String value = text(name, 40);
        // Enumerated wire values must be exact.
        if (!value.equals(object.get(name).asString()) || !Arrays.asList(values).contains(value)) {
            throw invalid(name, "ENUM");
        }
        return value;
    }

    public long nonNegativeLong(String name) {
        JsonNode value = required(name);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < 0) {
            throw invalid(name, "NON_NEGATIVE_INTEGER");
        }
        return value.asLong();
    }

    public int nonNegativeInt(String name) {
        long value = nonNegativeLong(name);
        if (value > Integer.MAX_VALUE) {
            throw invalid(name, "OUT_OF_RANGE");
        }
        return (int) value;
    }

    public boolean bool(String name) {
        JsonNode value = required(name);
        if (!value.isBoolean()) {
            throw invalid(name, "BOOLEAN");
        }
        return value.asBoolean();
    }

    public BigDecimal positiveDecimal(String name) {
        JsonNode value = required(name);
        if (!value.isNumber()) {
            throw invalid(name, "POSITIVE_NUMBER");
        }
        BigDecimal number = value.decimalValue();
        if (number.signum() <= 0 || number.precision() > 100 || Math.abs((long) number.scale()) > 100) {
            throw invalid(name, "POSITIVE_NUMBER");
        }
        return number;
    }

    public UUID id(String name) {
        String input = text(name, 36);
        try {
            UUID id = UUID.fromString(input);
            if (id.toString().equalsIgnoreCase(input)) {
                return id;
            }
        } catch (IllegalArgumentException ignored) {
            // All invalid identifiers use the same field error.
        }
        throw invalid(name, "FORMAT");
    }

    public ApiException invalid(String name, String code) {
        return ApiException.invalid(prefix + name, code);
    }

    private JsonNode required(String name) {
        if (blank(name)) {
            throw invalid(name, "REQUIRED");
        }
        return object.get(name);
    }
}
