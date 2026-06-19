package com.oppshan.washa.auth;

import com.oppshan.washa.user.UserAccountService;
import com.oppshan.washa.user.UserAccountView;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Returns the signed-in person (resolving or linking their Google identity against the
 * allowlist on the way). Requires authentication; the allowlist gate lives in the service.
 */
@Path("/api/me")
@Authenticated
public class MeEndpoint {

    private final SecurityIdentity identity;
    private final UserAccountService userAccountService;

    @Inject
    public MeEndpoint(SecurityIdentity identity, UserAccountService userAccountService) {
        this.identity = identity;
        this.userAccountService = userAccountService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public UserAccountView me() {
        return userAccountService.resolveOrLink(AuthSupport.idToken(identity));
    }
}
