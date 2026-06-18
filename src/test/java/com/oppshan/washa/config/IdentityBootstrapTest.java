package com.oppshan.washa.config;

import com.oppshan.washa.user.AllowedIdentityRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class IdentityBootstrapTest {

    @Inject
    IdentityBootstrap bootstrap;

    @Inject
    AllowedIdentityRepository allowedIdentityRepository;

    @Test
    void seedsPeopleAndEmailsIdempotently() {
        String json = """
                [{"firstName":"Alice","lastName":"Example",
                  "emails":["alice@example.com","alice.alt@example.com"]},
                 {"firstName":"Bob","lastName":"Example","emails":["bob@example.com"]}]""";

        bootstrap.seed(json);
        bootstrap.seed(json); // second run must not duplicate or throw

        assertThat(allowedIdentityRepository.findByEmail("alice@example.com")).isPresent();
        assertThat(allowedIdentityRepository.findByEmail("bob@example.com")).isPresent();

        UUID alice1 = allowedIdentityRepository.findByEmail("alice@example.com")
                .orElseThrow().getUserAccountUuid();
        UUID alice2 = allowedIdentityRepository.findByEmail("alice.alt@example.com")
                .orElseThrow().getUserAccountUuid();
        UUID bob = allowedIdentityRepository.findByEmail("bob@example.com")
                .orElseThrow().getUserAccountUuid();

        assertThat(alice2).isEqualTo(alice1); // Alice's two emails map to one person
        assertThat(bob).isNotEqualTo(alice1); // Bob is a distinct person
    }
}
