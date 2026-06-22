package com.oppshan.washa.config;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class AllowedIdentitiesParserTest {

    @Test
    void shouldParsePeopleAndTheirEmails() {
        final var json = """
                [{"firstName":"Alice","lastName":"Example",
                  "emails":["alice@example.com","alice.alt@example.com"]},
                 {"firstName":"Bob","lastName":"Example","emails":["bob@example.com"]}]""";

        final var people = AllowedIdentitiesParser.parse(json);

        assertThat(people, is(hasSize(2)));
        assertThat(people.getFirst().firstName(), is("Alice"));
        assertThat(people.getFirst().emails(),
                contains("alice@example.com", "alice.alt@example.com"));
        assertThat(people.get(1).emails(), contains("bob@example.com"));
    }

    @Test
    void shouldYieldEmptyListWhenArrayIsEmpty() {
        assertThat(AllowedIdentitiesParser.parse("[]"), is(empty()));
    }

    @Test
    void shouldYieldEmptyListWhenInputIsBlank() {
        assertThat(AllowedIdentitiesParser.parse("  "), is(empty()));
    }
}
