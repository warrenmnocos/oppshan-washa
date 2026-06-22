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
    void userAccountEqualityAndOrderingByName() {
        final var id = UUID.randomUUID();
        final var a = new UserAccount().setUuid(id).setFirstName("Alice").setLastName("Example");
        final var b = new UserAccount().setUuid(id).setFirstName("Alice").setLastName("Example");
        final var c = new UserAccount().setUuid(UUID.randomUUID()).setFirstName("Bob").setLastName("Example");

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c).isNotEqualTo(null).isNotEqualTo("x");
        assertThat(a.compareTo(c)).isNegative();   // Alice before Bob
        assertThat(c.compareTo(a)).isPositive();
    }

    @Test
    void userAccountComparatorToleratesNullFields() {
        final var named = new UserAccount().setUuid(UUID.randomUUID()).setFirstName("Alice");
        final var blank = new UserAccount().setUuid(UUID.randomUUID());
        final var sorted = new TreeSet<UserAccount>();
        sorted.add(named);
        sorted.add(blank);   // null first name sorts last — must not NPE
        assertThat(sorted).hasSize(2);
    }

    @Test
    void googleAccountEqualityAndProviderOrdering() {
        final var id = UUID.randomUUID();
        final var a = new GoogleAccount().setUuid(id).setProviderName("google").setProviderId("sub-1").setEmail("a@example.com");
        final var b = new GoogleAccount().setUuid(id).setProviderName("google").setProviderId("sub-1").setEmail("a@example.com");
        final var c = new GoogleAccount().setUuid(UUID.randomUUID()).setProviderName("google").setProviderId("sub-2").setEmail("c@example.com");

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a.compareTo(c)).isNegative();   // sub-1 before sub-2 within same provider
        assertThat(a.asGoogleAccount()).contains(a);
    }

    @Test
    void fxRateIdEqualityContract() {
        final var a = new FxRateId("JPY", "PHP");
        final var b = new FxRateId("JPY", "PHP");
        final var c = new FxRateId("JPY", "USD");

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c).isNotEqualTo(null).isNotEqualTo("x");
        assertThat(a.getBaseCurrency()).isEqualTo("JPY");
        assertThat(a.getQuoteCurrency()).isEqualTo("PHP");
    }

    @Test
    void currencySettingAccessors() {
        final var jpy = new CurrencySetting().setCode("JPY").setOrdinal(0).setSymbol("¥").setDecimals((short) 0);
        assertThat(jpy.getCode()).isEqualTo("JPY");
        assertThat(jpy.getOrdinal()).isZero();
        assertThat(jpy.getSymbol()).isEqualTo("¥");
        assertThat(jpy.getDecimals()).isZero();
    }
}
