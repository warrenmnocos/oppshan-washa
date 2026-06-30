package com.oppshan.washa.user;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the display-name fallback paths in {@link UserAccountService} that the endpoint tests
 * don't reach: when a linked person has no first/last name, the view falls back to the Google
 * account name, then to the email.
 */
@QuarkusTest
class UserAccountServiceTest {

    @Inject
    UserAccountService userAccountService;

    @Inject
    UserAccountRepository userAccountRepository;

    @Inject
    IdpAccountRepository idpAccountRepository;

    private void seedLinkedGoogle(String sub, String email, String googleName) {
        QuarkusTransaction.requiringNew().run(() -> {
            final var person = new UserAccount(); // no first/last name
            userAccountRepository.insertWithSession(person);
            idpAccountRepository.insertWithSession(new GoogleAccount()
                    .setUserAccount(person).setProviderName("google").setProviderId(sub)
                    .setEmail(email).setName(googleName));
        });
    }

    private JsonWebToken tokenFor(String sub) {
        final var jwt = mock(JsonWebToken.class);
        when(jwt.getSubject()).thenReturn(sub);
        return jwt;
    }

    @Test
    void shouldFallBackToGoogleNameWhenPersonHasNoName() {
        // Unique sub/email per run: the @QuarkusTest DB is shared across all test classes in a run,
        // so a fixed key would collide with another class's seed and break findGoogleByProvider's
        // single-result contract.
        final var sub = "sub-named-" + UUID.randomUUID();
        seedLinkedGoogle(sub, sub + "@example.com", "Google Name");

        final var view = QuarkusTransaction.requiringNew()
                .call(() -> userAccountService.resolveOrLink(tokenFor(sub)));

        assertThat(view.displayName(), is("Google Name"));
    }

    @Test
    void shouldFallBackToEmailWhenNeitherNameIsPresent() {
        final var sub = "sub-nameless-" + UUID.randomUUID();
        final var email = sub + "@example.com";
        seedLinkedGoogle(sub, email, null);

        final var view = QuarkusTransaction.requiringNew()
                .call(() -> userAccountService.resolveOrLink(tokenFor(sub)));

        assertThat(view.displayName(), is(email));
    }
}
