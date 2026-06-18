package com.oppshan.washa.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AllowedIdentitiesParserTest {

    @Test
    void parsesPeopleAndEmails() {
        String json = """
                [{"firstName":"Alice","lastName":"Example",
                  "emails":["alice@example.com","alice.alt@example.com"]},
                 {"firstName":"Bob","lastName":"Example","emails":["bob@example.com"]}]""";

        List<AllowedIdentitiesParser.Person> people = AllowedIdentitiesParser.parse(json);

        assertThat(people).hasSize(2);
        assertThat(people.get(0).firstName()).isEqualTo("Alice");
        assertThat(people.get(0).emails())
                .containsExactly("alice@example.com", "alice.alt@example.com");
        assertThat(people.get(1).emails()).containsExactly("bob@example.com");
    }

    @Test
    void emptyArrayYieldsEmptyList() {
        assertThat(AllowedIdentitiesParser.parse("[]")).isEmpty();
    }

    @Test
    void blankYieldsEmptyList() {
        assertThat(AllowedIdentitiesParser.parse("  ")).isEmpty();
    }
}
