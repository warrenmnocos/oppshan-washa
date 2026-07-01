package com.oppshan.washa.user;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;

/**
 * A view of the signed-in household person: their identity ({@code uuid}) plus display details
 * (first and last name, {@code displayName}, email, photo). {@code displayName} is derived, not a
 * stored column, so it's always a non-empty label. Registered for reflection so it can be
 * serialized in the native image.
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
