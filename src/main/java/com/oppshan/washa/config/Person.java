package com.oppshan.washa.config;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * One allow-listed household person as read from the {@code OPPSHAN_WASHA_ALLOWED_IDENTITIES} JSON:
 * a name plus the Google emails permitted to sign in as them.
 *
 * <p>{@code @RegisterForReflection} is load-bearing. This record is built only by Jackson from that
 * JSON, never reached through JAX-RS, so the native build won't auto-register it; without it the
 * native image fails to deserialize any non-empty allowlist at startup. That's why an empty
 * {@code []} default and the JVM-mode tests both pass while a real deploy would break (backend
 * CLAUDE.md A.11).
 */
@RegisterForReflection
public record Person(String firstName,
                     String lastName,
                     List<@NotEmpty String> emails) {
}
