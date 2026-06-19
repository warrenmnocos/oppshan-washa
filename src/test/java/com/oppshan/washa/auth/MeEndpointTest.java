package com.oppshan.washa.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class MeEndpointTest {

    @Test
    void shouldRejectAnonymousCallerAskingForCurrentUser() {
        given().redirects().follow(false)
                .when().get("/api/me")
                .then().statusCode(anyOf(is(401), is(302)));
    }

    @Test
    @TestSecurity(user = "sub-alice")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "sub-alice"),
            @Claim(key = "email", value = "alice@example.com"),
            @Claim(key = "email_verified", value = "true"),
            @Claim(key = "given_name", value = "Alice"),
            @Claim(key = "family_name", value = "Example"),
            @Claim(key = "name", value = "Alice Example"),
    })
    void shouldLinkAllowedUserThenFindOnSecondCall() {
        given().when().get("/api/me")
                .then().statusCode(200)
                .body("email", equalTo("alice@example.com"))
                .body("displayName", equalTo("Alice Example"));

        given().when().get("/api/me")
                .then().statusCode(200)
                .body("email", equalTo("alice@example.com"));
    }

    @Test
    @TestSecurity(user = "sub-mallory")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "sub-mallory"),
            @Claim(key = "email", value = "mallory@example.com"),
            @Claim(key = "email_verified", value = "true"),
    })
    void shouldDenyStrangerNotOnAllowlist() {
        given().when().get("/api/me").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "sub-alice2")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "sub-alice2"),
            @Claim(key = "email", value = "alice@example.com"),
            @Claim(key = "email_verified", value = "false"),
    })
    void shouldDenyWhenEmailNotVerified() {
        given().when().get("/api/me").then().statusCode(403);
    }
}
