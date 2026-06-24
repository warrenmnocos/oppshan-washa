package com.oppshan.washa.budget;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class SalaryPresetEndpointTest {

    @Test
    void shouldRejectAnonymousAccessToPresets() {
        given().redirects().follow(false)
                .when().get("/api/budget/presets")
                .then().statusCode(anyOf(is(401), is(302)));
    }

    @Test
    @TestSecurity(user = "alice")
    void shouldListBuiltInPresetsSeededOnStartup() {
        given().when().get("/api/budget/presets")
                .then().statusCode(200)
                // The four built-ins are seeded by SalaryPresetBootstrap and listed first.
                .body("name", hasItems("jp", "jp0", "ph", "blank"))
                // jp carries the Japanese payroll: a basic-salary component and five deductions.
                .body("find { it.name == 'jp' }.builtIn", is(true))
                .body("find { it.name == 'jp' }.salary.currency", equalTo("JPY"))
                .body("find { it.name == 'jp' }.salary.components[0].label", equalTo("Basic salary"))
                .body("find { it.name == 'jp' }.salary.deductions.size()", is(5))
                // jp0 is jp without the resident-tax line.
                .body("find { it.name == 'jp0' }.salary.deductions.size()", is(4))
                // ph's withholding tax is an additive bracket schedule (five rows).
                .body("find { it.name == 'ph' }.salary.currency", equalTo("PHP"))
                .body("find { it.name == 'ph' }.salary.deductions.find { it.label == 'Withholding tax' }.brackets.size()", is(5));
    }

    @Test
    @TestSecurity(user = "alice")
    void shouldCreateACustomPreset() {
        final var name = "custom-" + UUID.randomUUID();
        final var body = customPresetBody(name);

        final var uuid = given().contentType("application/json").body(body)
                .when().post("/api/budget/presets")
                .then().statusCode(201)
                .body("name", equalTo(name))
                .body("builtIn", is(false))
                .body("uuid", is(notNullValue()))
                .body("salary.currency", equalTo("PHP"))
                .body("salary.components[0].amount", equalTo(300000))
                .extract().path("uuid");

        // It is now listed alongside the built-ins.
        given().when().get("/api/budget/presets")
                .then().statusCode(200)
                .body("find { it.uuid == '" + uuid + "' }.name", equalTo(name));
    }

    @Test
    @TestSecurity(user = "alice")
    void shouldDeleteACustomPreset() {
        final var name = "deletable-" + UUID.randomUUID();

        final var uuid = given().contentType("application/json").body(customPresetBody(name))
                .when().post("/api/budget/presets")
                .then().statusCode(201)
                .extract().path("uuid").toString();

        given().when().delete("/api/budget/presets/" + uuid)
                .then().statusCode(204);

        // It is gone from the list.
        given().when().get("/api/budget/presets")
                .then().statusCode(200)
                .body("findAll { it.uuid == '" + uuid + "' }.size()", is(0));
    }

    @Test
    @TestSecurity(user = "alice")
    void shouldRejectDeletingABuiltInPreset() {
        final var jpUuid = given().when().get("/api/budget/presets")
                .then().statusCode(200)
                .extract().path("find { it.name == 'jp' }.uuid").toString();

        given().when().delete("/api/budget/presets/" + jpUuid)
                .then().statusCode(400)
                .body("messageCode", equalTo("messages.errors.salaryPresetBuiltIn"));

        // The built-in survives the rejected delete.
        given().when().get("/api/budget/presets")
                .then().statusCode(200)
                .body("find { it.name == 'jp' }.uuid", equalTo(jpUuid));
    }

    @Test
    @TestSecurity(user = "alice")
    void shouldRejectDeletingAMissingPreset() {
        given().when().delete("/api/budget/presets/" + UUID.randomUUID())
                .then().statusCode(400)
                .body("messageCode", equalTo("messages.errors.salaryPresetNotFound"));
    }

    private static String customPresetBody(String name) {
        return """
                {"name":"%s",
                 "salary":{"name":"%s","currency":"PHP","engine":"generic",
                   "components":[{"label":"Basic salary","amount":300000,"taxable":true,"basic":true}],
                   "deductions":[{"label":"SSS","kind":"deductionType.pct","base":"deductionBase.basic",
                     "rate":5,"cap":1750,"floor":250,"pretax":true,"brackets":[]}],
                   "variables":[]}}""".formatted(name, name);
    }
}
