package com.oppshan.washa.config;

import io.smallrye.config.ConfigMapping;

/**
 * Binds {@code washa.allowed-identities} (the raw JSON from Parameter Store) so it can be
 * injected and seeded at startup by {@link IdentityBootstrap}.
 */
@ConfigMapping(prefix = "washa")
public interface AllowedIdentitiesConfig {

    String allowedIdentities();
}
