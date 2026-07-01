package com.oppshan.washa.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Binds {@code oppshan.washa.allowed-identities} (the raw allowlist JSON from Parameter Store) as a
 * typed config property. The dotted prefix relaxed-binds to the
 * {@code OPPSHAN_WASHA_ALLOWED_IDENTITIES} env var the Lambda supplies; absent it (plain dev, an
 * un-overridden profile), {@code @WithDefault} keeps the allowlist an empty {@code []}.
 */
@ConfigMapping(prefix = "oppshan.washa")
public interface AllowedIdentitiesConfig {

    /** The raw allowlist JSON, or {@code "[]"} when the env var is unset. */
    @WithDefault("[]")
    String allowedIdentities();
}
