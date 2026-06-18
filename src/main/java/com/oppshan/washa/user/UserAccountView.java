package com.oppshan.washa.user;

import java.util.UUID;

public record UserAccountView(
        UUID uuid,
        String firstName,
        String lastName,
        String displayName,
        String email,
        String photoUrl) {
}
