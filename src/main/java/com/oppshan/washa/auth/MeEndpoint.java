package com.oppshan.washa.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Reports the signed-in household person, or {@code 401} when signed out. Deliberately NOT
 * {@code @Authenticated}: the SPA polls this to choose between the app and the public login page, so
 * an anonymous caller must get a clean {@code 401} rather than an OIDC redirect to Google. A
 * signed-in but non-allowlisted Google identity surfaces as {@code 403} from the allowlist gate in
 * {@link UserSessionManager#sessionUserAccount()}.
 */
@Path("/api/me")
public class MeEndpoint {

    private final UserSessionManager userSessionManager;

    /** Injects the session manager this endpoint delegates its session check and person lookup to. */
    @Inject
    public MeEndpoint(UserSessionManager userSessionManager) {
        this.userSessionManager = userSessionManager;
    }

    /**
     * Returns the signed-in person as a {@code UserAccountView}, or a bare {@code 401} when the request
     * carries no session. A signed-in identity that isn't on the allowlist propagates as {@code 403}
     * from {@link UserSessionManager#sessionUserAccount()} (via the BusinessException mapper), not
     * handled here.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response me() {
        if (userSessionManager.isSignedOut()) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        return Response.ok(userSessionManager.sessionUserAccount()).build();
    }
}
