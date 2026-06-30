package com.oppshan.washa.config;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

// Constructed only by Jackson from the OPPSHAN_WASHA_ALLOWED_IDENTITIES JSON — never reached through
// JAX-RS — so the native build won't auto-register it for reflection. Without this the native
// image fails to deserialize any non-empty allowlist at startup (empty [] never builds a Person,
// which is why JVM tests and an empty default both pass). See backend CLAUDE.md A.11.
@RegisterForReflection
public record Person(String firstName,
                     String lastName,
                     List<@NotEmpty String> emails) {
}
