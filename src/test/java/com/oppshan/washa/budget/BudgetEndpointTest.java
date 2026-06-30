package com.oppshan.washa.budget;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class BudgetEndpointTest {

    @Inject
    FxRateRepository fxRateRepository;

    /** A random three-letter uppercase currency code, unique per run (A.10 shared-DB collisions). */
    private static String randomCurrencyCode() {
        return UUID.randomUUID().toString().replaceAll("[^a-zA-Z]", "")
                .substring(0, 3).toUpperCase();
    }

    private static final String ONE_SALARY_MONTH = """
            {"salaries":[{"name":"Alice","currency":"JPY","engine":"generic",
              "components":[{"label":"Basic salary","amount":500000,"taxable":true,"basic":true}],
              "deductions":[],"variables":[]}],
             "expenses":[{"label":"Rent","amt":150000,"cur":"JPY"}],
             "goals":[],"debts":[],"cur":[{"code":"JPY","sym":"¥"}]}""";

    @Test
    void shouldRejectAnonymousAccessToBudget() {
        given().redirects().follow(false)
                .when().get("/api/budget/fx")
                .then().statusCode(anyOf(is(401), is(302)));
    }

    @Test
    @TestSecurity(user = "alice")
    void shouldReturnFxRatesWithConservativeDefault() {
        // The conservative default applies only when JPY has no stored rates at all; a saved month or
        // an earlier test may have persisted some (saveMonth upserts fx rates), so clear every JPY rate
        // first to assert the default (A.10: control shared DB state).
        QuarkusTransaction.requiringNew().run(() ->
                fxRateRepository.findByBaseCurrency("JPY")
                        .forEach(rate -> fxRateRepository.deleteWithSession(
                                fxRateRepository.attachWithSession(rate))));

        given().when().get("/api/budget/fx?base=JPY")
                .then().statusCode(200)
                .body("PHP", equalTo(0.36f));
    }

    @Test
    @TestSecurity(user = "alice")
    void shouldPersistAnUpsertedRateSoRatesReflectsIt() {
        final var base = randomCurrencyCode();
        final var quote = randomCurrencyCode();

        given().contentType("application/json")
                .body("{\"base\":\"" + base + "\",\"quote\":\"" + quote + "\",\"rate\":0.42}")
                .when().put("/api/budget/fx")
                .then().statusCode(200)
                .body(quote, equalTo(0.42f));

        given().when().get("/api/budget/fx?base=" + base)
                .then().statusCode(200)
                .body(quote, equalTo(0.42f));
    }

    @Test
    @TestSecurity(user = "alice")
    void shouldOverwriteRatherThanDuplicateOnASecondUpsert() {
        final var base = randomCurrencyCode();
        final var quote = randomCurrencyCode();

        given().contentType("application/json")
                .body("{\"base\":\"" + base + "\",\"quote\":\"" + quote + "\",\"rate\":0.50}")
                .when().put("/api/budget/fx")
                .then().statusCode(200);

        // A second upsert of the same pair overwrites the row (a single value, not a duplicate row
        // that would fail rates()' map keyed by quote currency).
        given().contentType("application/json")
                .body("{\"base\":\"" + base + "\",\"quote\":\"" + quote + "\",\"rate\":0.75}")
                .when().put("/api/budget/fx")
                .then().statusCode(200)
                .body(quote, equalTo(0.75f));

        given().when().get("/api/budget/fx?base=" + base)
                .then().statusCode(200)
                .body(quote, equalTo(0.75f));
    }

    @Test
    @TestSecurity(user = "alice")
    void shouldRejectANonPositiveRate() {
        given().contentType("application/json")
                .body("{\"base\":\"" + randomCurrencyCode() + "\",\"quote\":\""
                        + randomCurrencyCode() + "\",\"rate\":0}")
                .when().put("/api/budget/fx")
                .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "alice")
    void shouldComputeMoneyInAndTitheFromPostedMonth() {
        given().contentType("application/json").body(ONE_SALARY_MONTH)
                .when().post("/api/budget/compute")
                .then().statusCode(200)
                .body("moneyIn", equalTo(500000))   // ¥500,000 net (no deductions)
                .body("moneyOut", equalTo(150000))  // ¥150,000 rent
                .body("free", equalTo(350000))
                .body("tithe", equalTo(50000.0f));  // 10% of net
    }

    @Test
    @TestSecurity(user = "alice")
    void shouldComputeAsOfTheGivenMonthWhenItsKeyIsSupplied() {
        // The month-aware path: the same figures hold (no prior-month goal contributions to
        // accumulate here), and the as-of key is parsed and threaded through to the engine.
        given().contentType("application/json").body(ONE_SALARY_MONTH)
                .when().post("/api/budget/compute?month=2026-07")
                .then().statusCode(200)
                .body("moneyIn", equalTo(500000))
                .body("free", equalTo(350000))
                .body("tithe", equalTo(50000.0f));
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
    void shouldRoundTripMonthThroughPutThenGet() {
        given().contentType("application/json").body(ONE_SALARY_MONTH)
                .when().put("/api/budget/month/2026-09")
                .then().statusCode(200)
                .body("salaries[0].name", equalTo("Alice"));

        given().when().get("/api/budget/month/2026-09")
                .then().statusCode(200)
                .body("salaries[0].components[0].amount", equalTo(500000.0f))
                .body("expenses[0].label", equalTo("Rent"));
    }
}
