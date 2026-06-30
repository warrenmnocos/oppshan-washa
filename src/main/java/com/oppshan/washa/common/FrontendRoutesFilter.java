package com.oppshan.washa.common;

import io.quarkus.vertx.web.RouteFilter;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.regex.Pattern;

/**
 * SPA history fallback (mirrors oppshan-files). The Angular app is a single page with client-side
 * routing, so a hard navigation or refresh on a client route (e.g. {@code /sso/sign-in},
 * {@code /budget}) must return {@code index.html} for the router to take over — otherwise the
 * request 404s, there being no static file at that path. Backend paths are left to their own
 * handlers: the REST API ({@code /api/**}), the Quarkus management namespace ({@code /q/**}),
 * static assets, sign-out, and the OIDC trigger/callback ({@code /sso/sign-in/oidc/**}). Everything
 * else — notably the {@code /sso/sign-in} page itself — is served by the SPA.
 */
@ApplicationScoped
public class FrontendRoutesFilter {

    private static final Pattern STATIC_ASSET = Pattern.compile(
            ".+\\.(js|mjs|css|html|htm|json|map|webmanifest|wasm|png|jpe?g|gif|svg|webp|ico|woff2?|ttf|eot|otf|txt|xml|pdf)$",
            Pattern.CASE_INSENSITIVE);

    @RouteFilter(100)
    void rerouteClientPathsToIndex(RoutingContext routingContext) {
        if (servesFromBackend(routingContext.normalizedPath())) {
            routingContext.next();
            return;
        }

        routingContext.reroute("/index.html");
    }

    private static boolean servesFromBackend(String path) {
        return path.startsWith("/api/")
                || path.startsWith("/q/")
                || path.startsWith("/sso/sign-out")
                || path.startsWith("/sso/sign-in/oidc/")
                || STATIC_ASSET.matcher(path).matches();
    }
}
