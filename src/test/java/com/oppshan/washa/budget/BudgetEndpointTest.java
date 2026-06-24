package com.oppshan.washa.budget;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class BudgetEndpointTest {

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
        given().when().get("/api/budget/fx?base=JPY")
                .then().statusCode(200)
                .body("PHP", equalTo(0.36f));
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
