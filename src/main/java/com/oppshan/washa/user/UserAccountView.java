package com.oppshan.washa.user;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;

/**
 * The signed-in household person — the current user's identity and display details (names, email,
 * photo), returned by {@code /api/me}.
 */
@RegisterForReflection
public record UserAccountView(
        UUID uuid,
        String firstName,
        String lastName,
        String displayName,
        String email,
        String photoUrl) {
}
