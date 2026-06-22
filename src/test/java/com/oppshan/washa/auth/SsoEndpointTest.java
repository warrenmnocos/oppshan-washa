package com.oppshan.washa.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;

@QuarkusTest
class SsoEndpointTest {

    @Test
    @TestSecurity(user = "sub-alice")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "sub-alice"),
            @Claim(key = "email", value = "alice@example.com"),
            @Claim(key = "email_verified", value = "true"),
            @Claim(key = "given_name", value = "Alice"),
            @Claim(key = "family_name", value = "Example"),
    })
    void shouldLinkAndRedirectHomeOnSignIn() {
        given().redirects().follow(false)
                .when().get("/sso/sign-in")
                .then().statusCode(303)
                .header("Location", endsWith("/"));
    }

    @Test
    @TestSecurity(user = "sub-alice")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "sub-alice"),
            @Claim(key = "email", value = "alice@example.com"),
            @Claim(key = "email_verified", value = "true"),
    })
    void shouldLinkAndRedirectHomeOnOidcCallback() {
        given().redirects().follow(false)
                .when().get("/sso/sign-in/oidc/callback/google")
                .then().statusCode(303)
                .header("Location", endsWith("/"));
    }
}
