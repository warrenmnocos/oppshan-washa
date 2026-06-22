package com.oppshan.washa.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AllowedIdentitiesParserTest {

    @Test
    void shouldParsePeopleAndTheirEmails() {
        final var json = """
                [{"firstName":"Alice","lastName":"Example",
                  "emails":["alice@example.com","alice.alt@example.com"]},
                 {"firstName":"Bob","lastName":"Example","emails":["bob@example.com"]}]""";

        final var people = AllowedIdentitiesParser.parse(json);

        assertThat(people).hasSize(2);
        assertThat(people.getFirst().firstName()).isEqualTo("Alice");
        assertThat(people.getFirst().emails())
                .containsExactly("alice@example.com", "alice.alt@example.com");
        assertThat(people.get(1).emails()).containsExactly("bob@example.com");
    }

    @Test
    void shouldYieldEmptyListWhenArrayIsEmpty() {
        assertThat(AllowedIdentitiesParser.parse("[]")).isEmpty();
    }

    @Test
    void shouldYieldEmptyListWhenInputIsBlank() {
        assertThat(AllowedIdentitiesParser.parse("  ")).isEmpty();
    }
}
