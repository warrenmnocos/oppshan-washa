package com.oppshan.washa.auth;

import com.oppshan.washa.user.UserAccountService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import java.net.URI;

/**
 * SSO entry points. Hitting an {@link Authenticated} path while signed out triggers the Google
 * OIDC code flow; Quarkus handles the callback (redirect-path) and lands here, where the identity
 * is linked and the user is sent to the dashboard. Sign-out is handled by Quarkus OIDC logout
 * (configured via {@code quarkus.oidc.logout.path}).
 */
@Path("/sso")
public class SsoEndpoint {

    private static final URI HOME = URI.create("/");

    private final SecurityIdentity identity;
    private final UserAccountService userAccountService;

    @Inject
    public SsoEndpoint(SecurityIdentity identity, UserAccountService userAccountService) {
        this.identity = identity;
        this.userAccountService = userAccountService;
    }

    @GET
    @Path("/sign-in")
    @Authenticated
    public Response signIn() {
        userAccountService.resolveOrLink(AuthSupport.idToken(identity));
        return Response.seeOther(HOME).build();
    }

    @GET
    @Path("/sign-in/oidc/callback/google")
    @Authenticated
    public Response callback() {
        userAccountService.resolveOrLink(AuthSupport.idToken(identity));
        return Response.seeOther(HOME).build();
    }
}
