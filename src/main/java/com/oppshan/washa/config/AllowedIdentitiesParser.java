package com.oppshan.washa.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Parses the {@code WASHA_ALLOWED_IDENTITIES} JSON (from Parameter Store): a list of people,
 * each with a name and the Google emails permitted for them. No real identities live in source.
 */
public final class AllowedIdentitiesParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AllowedIdentitiesParser() {
    }

    public static List<Person> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<Person>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid WASHA_ALLOWED_IDENTITIES JSON", e);
        }
    }

    public record Person(String firstName, String lastName, List<String> emails) {
    }
}
