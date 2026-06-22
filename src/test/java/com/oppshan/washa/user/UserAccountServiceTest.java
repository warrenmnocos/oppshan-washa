package com.oppshan.washa.user;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
        seedLinkedGoogle("sub-named", "named@example.com", "Google Name");

        final var view = QuarkusTransaction.requiringNew()
                .call(() -> userAccountService.resolveOrLink(tokenFor("sub-named")));

        assertThat(view.displayName()).isEqualTo("Google Name");
    }

    @Test
    void shouldFallBackToEmailWhenNeitherNameIsPresent() {
        seedLinkedGoogle("sub-nameless", "nameless@example.com", null);

        final var view = QuarkusTransaction.requiringNew()
                .call(() -> userAccountService.resolveOrLink(tokenFor("sub-nameless")));

        assertThat(view.displayName()).isEqualTo("nameless@example.com");
    }
}
