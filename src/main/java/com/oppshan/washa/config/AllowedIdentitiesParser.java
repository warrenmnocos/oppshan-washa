package com.oppshan.washa.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;

/**
 * Turns the {@code OPPSHAN_WASHA_ALLOWED_IDENTITIES} JSON (from Parameter Store) into a list of
 * {@link Person} records, each a name plus the Google emails permitted to sign in as them. Kept as a
 * plain static parser (no CDI) so it stays pure and cheap to unit-test. No real identities live in
 * source; the JSON only exists at runtime.
 */
public final class AllowedIdentitiesParser {

    /** Shared and thread-safe once configured, so no per-call ObjectMapper allocation. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** No instances; this is a static utility. */
    private AllowedIdentitiesParser() {
    }

    /**
     * Parses the allowlist JSON into people. A null or blank string yields an empty list (defensive:
     * the configured default is {@code "[]"}, which Jackson parses to empty anyway). Any parse failure
     * is rethrown as {@link IllegalArgumentException} so a malformed value fails loudly instead of
     * silently yielding an empty allowlist. The anonymous {@code TypeReference<>() {}} captures the
     * {@code List<Person>} target from this method's return type, which is what tells Jackson to build
     * {@code Person} elements rather than maps.
     */
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
