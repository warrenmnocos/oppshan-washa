package com.oppshan.washa.common;

import io.quarkus.vertx.web.RouteFilter;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.regex.Pattern;

/**
 * Single-page-app history fallback (mirrors oppshan-files). Reroutes any request that isn't a
 * backend-owned path to {@code /index.html}. A hard navigation or refresh on a client route
 * (e.g. {@code /sso/sign-in}, {@code /budget}) has no static file behind it, so without the reroute
 * it would 404; serving {@code /index.html} instead lets the single-page app resolve the route on
 * the client. Backend paths keep their own handlers: the REST API ({@code /api/**}), the Quarkus
 * management namespace ({@code /q/**}), static assets, sign-out, and the OIDC trigger/callback
 * ({@code /sso/sign-in/oidc/**}). Everything else, notably the {@code /sso/sign-in} page itself, is
 * rerouted to {@code /index.html}.
 */
@ApplicationScoped
public class FrontendRoutesFilter {

    private static final Pattern STATIC_ASSET = Pattern.compile(
            ".+\\.(js|mjs|css|html|htm|json|map|webmanifest|wasm|png|jpe?g|gif|svg|webp|ico|woff2?|ttf|eot|otf|txt|xml|pdf)$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Passes a backend-owned path through to the next handler; reroutes anything else to
     * {@code /index.html}.
     */
    @RouteFilter(100)
    void rerouteClientPathsToIndex(RoutingContext routingContext) {
        if (servesFromBackend(routingContext.normalizedPath())) {
            routingContext.next();
            return;
        }

        routingContext.reroute("/index.html");
    }

    /**
     * Whether the path is handled by the backend rather than rerouted to the single-page app: the
     * REST API, the Quarkus management namespace, the {@code /sso/sign-out} and OIDC
     * {@code /sso/sign-in/oidc/} paths, or a static asset matched by file extension.
     */
    private static boolean servesFromBackend(String path) {
        return path.startsWith("/api/")
                || path.startsWith("/q/")
                || path.startsWith("/sso/sign-out")
                || path.startsWith("/sso/sign-in/oidc/")
                || STATIC_ASSET.matcher(path).matches();
    }
}
