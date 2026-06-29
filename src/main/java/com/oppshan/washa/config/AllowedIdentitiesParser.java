package com.oppshan.washa.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;

/**
 * Parses the {@code WASHA_ALLOWED_IDENTITIES} JSON (from Parameter Store): a list of people, each with a name and the
 * Google emails permitted for them. No real identities live in source.
 */
public final class AllowedIdentitiesParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AllowedIdentitiesParser() {
    }

    public static List<Person> parse(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        try {
            return MAPPER.readValue(
                    json,
                    new TypeReference<>() {
                    }
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid JSON", ex);
        }
    }
}
