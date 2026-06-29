package com.oppshan.washa.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.annotations.RegisterForReflection;

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

    // Constructed only by Jackson from the WASHA_ALLOWED_IDENTITIES JSON — never reached through
    // JAX-RS — so the native build won't auto-register it for reflection. Without this the native
    // image fails to deserialize any non-empty allowlist at startup (empty [] never builds a Person,
    // which is why JVM tests and an empty default both pass). See backend CLAUDE.md A.11.
    @RegisterForReflection
    public record Person(String firstName, String lastName, List<String> emails) {
    }
}
