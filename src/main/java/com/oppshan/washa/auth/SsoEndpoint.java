package com.oppshan.washa.auth;

import com.oppshan.washa.exception.BusinessException;
import com.oppshan.washa.exception.MessageCode;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import java.net.URI;

/**
 * Backend SSO hops under {@code /sso}. The public sign-in <em>page</em> is an Angular route
 * ({@code /sso/sign-in}, served by the SPA fallback); these are the
 * server endpoints it drives:
 *
 * <ul>
 *   <li>{@code /sso/sign-in/oidc/google} — an {@link Authenticated} path; hitting it signed out
 *       kicks off the Google OIDC code flow.</li>
 *   <li>{@code /sso/sign-in/oidc/callback/google} — the OIDC redirect target (Quarkus
 *       {@code redirect-path}); the identity is linked against the allowlist here.</li>
 *   <li>{@code /sso/sign-out} — local logout.</li>
 * </ul>
 *
 * <p>A non-allowlisted identity is signed back out and returned to the sign-in page with a message.
 * Sign-out is a local logout: Google advertises no {@code end_session_endpoint}, so RP-Initiated
 * Logout is unavailable.
 */
@Path("/sso")
public class SsoEndpoint {

    /** The SPA root; every successful hop redirects here. */
    private static final URI HOME = URI.create("/");

    /** Back to the public sign-in page, carrying the access-denied code so the SPA can show why. */
    private static final URI SIGN_IN_DENIED =
            URI.create("/sso/sign-in?message=" + MessageCode.ACCESS_DENIED.getKey());

    private final UserSessionManager userSessionManager;

    /** Injects the session manager these hops use to resolve/link the identity and to sign out. */
    @Inject
    public SsoEndpoint(UserSessionManager userSessionManager) {
        this.userSessionManager = userSessionManager;
    }

    /**
     * Kicks off sign-in. {@code @Authenticated} on a signed-out caller makes Quarkus start the Google
     * code flow; once the browser returns authenticated, {@link #linkAndLand()} resolves the identity
     * against the allowlist and redirects home.
     */
    @GET
    @Path("/sign-in/oidc/google")
    @Authenticated
    public Response signInViaOidc() {
        return linkAndLand();
    }

    /**
     * The OIDC redirect target Google sends the browser back to (Quarkus {@code redirect-path}). Same
     * {@link #linkAndLand()} tail as {@link #signInViaOidc()}: resolve against the allowlist and redirect
     * home, or bounce to the sign-in page if the identity is denied.
     */
    @GET
    @Path("/sign-in/oidc/callback/google")
    @Authenticated
    public Response callback() {
        return linkAndLand();
    }

    /** Local logout, then redirect to the SPA root. */
    @GET
    @Path("/sign-out")
    public Response signOut() {
        userSessionManager.signOut();
        return Response.seeOther(HOME).build();
    }

    /**
     * Shared tail for both sign-in hops. Forces the allowlist resolve/link, then redirects home. If the
     * identity isn't allowlisted, {@link UserSessionManager#sessionUserAccount()} throws
     * {@link BusinessException}: catch it, sign the half-authenticated session back out, and redirect to
     * the sign-in page with the access-denied code so the SPA can explain the rejection.
     */
    private Response linkAndLand() {
        try {
            userSessionManager.sessionUserAccount();
            return Response.seeOther(HOME).build();
        } catch (BusinessException denied) {
            userSessionManager.signOut();
            return Response.seeOther(SIGN_IN_DENIED).build();
        }
    }
}
