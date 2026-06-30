package com.oppshan.washa.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Binds {@code oppshan.washa.allowed-identities} (the raw JSON from Parameter Store) so it can be
 * injected and seeded at startup by {@link IdentityBootstrap}. The dotted prefix relaxed-binds to
 * the {@code OPPSHAN_WASHA_ALLOWED_IDENTITIES} env var the Lambda supplies; absent it (plain dev,
 * an un-overridden profile), {@code @WithDefault} keeps the allowlist an empty {@code []}.
 */
@ConfigMapping(prefix = "oppshan.washa")
public interface AllowedIdentitiesConfig {

    @WithDefault("[]")
    String allowedIdentities();
}
