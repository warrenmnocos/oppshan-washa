package com.oppshan.washa.user;

import com.oppshan.washa.budget.CurrencySetting;
import com.oppshan.washa.budget.FxRateId;
import org.junit.jupiter.api.Test;

import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** equals / hashCode / compareTo contracts for entities and value objects. */
class EntityContractTest {

    @Test
    void shouldOrderUserAccountsByNameAndHonourEquality() {
        final var id = UUID.randomUUID();
        final var a = new UserAccount().setUuid(id).setFirstName("Alice").setLastName("Example");
        final var b = new UserAccount().setUuid(id).setFirstName("Alice").setLastName("Example");
        final var c = new UserAccount().setUuid(UUID.randomUUID()).setFirstName("Bob").setLastName("Example");

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c).isNotEqualTo(null).isNotEqualTo("x");
        assertThat(a.compareTo(c)).isNegative();   // Alice before Bob
        assertThat(c.compareTo(a)).isPositive();

        // Differ only by a field after uuid → exercises the firstName/lastName equals branches.
        final var sameIdDifferentFirst = new UserAccount().setUuid(id).setFirstName("Alicia").setLastName("Example");
        final var sameIdDifferentLast = new UserAccount().setUuid(id).setFirstName("Alice").setLastName("Other");
        assertThat(a).isNotEqualTo(sameIdDifferentFirst).isNotEqualTo(sameIdDifferentLast);
    }

    @Test
    void shouldBreakUserAccountTiesByLastNameThenUuid() {
        final var lo = new UserAccount().setUuid(UUID.randomUUID()).setFirstName("Alice").setLastName("Aa");
        final var hi = new UserAccount().setUuid(UUID.randomUUID()).setFirstName("Alice").setLastName("Zz");
        assertThat(lo.compareTo(hi)).isNegative();   // same first name → ordered by last name

        final var id = UUID.fromString("00000000-0000-7000-8000-000000000001");
        final var id2 = UUID.fromString("00000000-0000-7000-8000-000000000002");
        final var u1 = new UserAccount().setUuid(id).setFirstName("Alice").setLastName("Example");
        final var u2 = new UserAccount().setUuid(id2).setFirstName("Alice").setLastName("Example");
        assertThat(u1.compareTo(u2)).isNegative();   // same names → ordered by uuid tie-breaker
    }

    @Test
    void shouldTolerateNullFieldsInUserAccountComparator() {
        final var named = new UserAccount().setUuid(UUID.randomUUID()).setFirstName("Alice");
        final var blank = new UserAccount().setUuid(UUID.randomUUID());
        final var sorted = new TreeSet<UserAccount>();
        sorted.add(named);
        sorted.add(blank);   // null first name sorts last — must not NPE
        assertThat(sorted).hasSize(2);
    }

    @Test
    void shouldHonourGoogleAccountEqualityAndProviderOrdering() {
        final var id = UUID.randomUUID();
        final var a = new GoogleAccount().setUuid(id).setProviderName("google").setProviderId("sub-1").setEmail("a@example.com");
        final var b = new GoogleAccount().setUuid(id).setProviderName("google").setProviderId("sub-1").setEmail("a@example.com");
        final var c = new GoogleAccount().setUuid(UUID.randomUUID()).setProviderName("google").setProviderId("sub-2").setEmail("c@example.com");

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a.compareTo(c)).isNegative();   // sub-1 before sub-2 within same provider
        assertThat(a.asGoogleAccount()).contains(a);
    }

    @Test
    void shouldHonourFxRateIdEqualityContract() {
        final var a = new FxRateId("JPY", "PHP");
        final var b = new FxRateId("JPY", "PHP");
        final var c = new FxRateId("JPY", "USD");

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c).isNotEqualTo(null).isNotEqualTo("x");
        assertThat(a).isNotEqualTo(new FxRateId("USD", "PHP"));   // differ by base currency
        assertThat(a.getBaseCurrency()).isEqualTo("JPY");
        assertThat(a.getQuoteCurrency()).isEqualTo("PHP");
    }

    @Test
    void shouldExposeCurrencySettingAccessors() {
        final var jpy = new CurrencySetting().setCode("JPY").setOrdinal(0).setSymbol("¥").setDecimals((short) 0);
        assertThat(jpy.getCode()).isEqualTo("JPY");
        assertThat(jpy.getOrdinal()).isZero();
        assertThat(jpy.getSymbol()).isEqualTo("¥");
        assertThat(jpy.getDecimals()).isZero();
    }
}
