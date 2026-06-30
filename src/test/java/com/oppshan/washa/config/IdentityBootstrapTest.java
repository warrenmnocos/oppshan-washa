package com.oppshan.washa.config;

import com.oppshan.washa.user.AllowedIdentityRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class IdentityBootstrapTest {

    @Inject
    IdentityBootstrap bootstrap;

    @Inject
    AllowedIdentityRepository allowedIdentityRepository;

    @Test
    void shouldSeedPeopleAndEmailsIdempotently() {
        final var json = """
                [{"firstName":"Alice","lastName":"Example",
                  "emails":["alice@example.com","alice.alt@example.com"]},
                 {"firstName":"Bob","lastName":"Example","emails":["bob@example.com"]}]""";

        bootstrap.seed(json);
        bootstrap.seed(json); // second run must not duplicate or throw

        assertThat(allowedIdentityRepository.findByEmail("alice@example.com").isPresent(), is(true));
        assertThat(allowedIdentityRepository.findByEmail("bob@example.com").isPresent(), is(true));

        final var alice1 = allowedIdentityRepository.findByEmail("alice@example.com")
                .orElseThrow().getUserAccountUuid();
        final var alice2 = allowedIdentityRepository.findByEmail("alice.alt@example.com")
                .orElseThrow().getUserAccountUuid();
        final var bob = allowedIdentityRepository.findByEmail("bob@example.com")
                .orElseThrow().getUserAccountUuid();

        assertThat(alice2, is(alice1)); // Alice's two emails map to one person
        assertThat(bob, is(not(alice1))); // Bob is a distinct person
    }
}
