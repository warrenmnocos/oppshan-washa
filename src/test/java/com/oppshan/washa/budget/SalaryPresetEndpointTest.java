package com.oppshan.washa.budget;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * The list endpoint's body (every preset, ~25KB once the Japan/Philippines tax schedules are
 * included) is too large for the @QuarkusTest amazon-lambda mock event server to stream back to the
 * test client: the status line returns but the body read hangs until the socket times out. That is a
 * test-transport limit only — the deployed Lambda uses the real runtime, not this mock — so these
 * tests assert the list's CONTENT through {@link SalaryPresetService} (the same call the endpoint
 * delegates to) and exercise the HTTP layer for authorization, status, and the small-bodied POST /
 * DELETE responses, which the mock delivers fine.
 */
@QuarkusTest
class SalaryPresetEndpointTest {

    @Inject
    SalaryPresetService salaryPresetService;

    @Test
    void shouldRejectAnonymousAccessToPresets() {
        given().redirects().follow(false)
                .when().get("/api/budget/presets")
                .then().statusCode(anyOf(is(401), is(302)));
    }

    @Test
    void shouldListBuiltInPresetsSeededOnStartup() {
        // The list body is too large for the lambda test mock to stream back — even a status-only GET
        // buffers and times out on it — so the seeded content is asserted at the service layer. The
        // endpoint's authorization is covered by shouldRejectAnonymousAccessToPresets.
        final var presets = listPresets();
        assertThat(presets.stream().map(SalaryPresetView::name).toList(),
                hasItems("Japan", "Japan No Resident Tax", "Philippines", "blank"));

        // Japan carries the Japanese payroll: a basic-salary component and five deductions.
        final var japan = presetNamed(presets, "Japan");
        assertThat(japan.builtIn(), is(true));
        assertThat(japan.salary().currency(), equalTo("JPY"));
        assertThat(japan.salary().components().getFirst().label(), equalTo("Basic salary"));
        assertThat(japan.salary().deductions().size(), is(5));
        // "Japan No Resident Tax" is Japan without the resident-tax line.
        assertThat(presetNamed(presets, "Japan No Resident Tax").salary().deductions().size(), is(4));
        // The Philippines withholding tax is an additive bracket schedule (five rows).
        final var philippines = presetNamed(presets, "Philippines");
        assertThat(philippines.salary().currency(), equalTo("PHP"));
        assertThat(deductionNamed(philippines, "Withholding tax").brackets().size(), is(5));
    }

    @Test
    @TestSecurity(user = "alice")
    void shouldCreateACustomPreset() {
        final var name = "custom-" + UUID.randomUUID();

        final var uuid = given().contentType("application/json").body(customPresetBody(name))
                .when().post("/api/budget/presets")
                .then().statusCode(201)
                .body("name", equalTo(name))
                .body("builtIn", is(false))
                .body("uuid", is(notNullValue()))
                .body("salary.currency", equalTo("PHP"))
                .body("salary.components[0].amount", equalTo(300000))
                .extract().path("uuid").toString();

        // It is now in the shared store, listed by name.
        assertThat(namesWithUuid(uuid), contains(name));
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

        // It is gone from the store.
        assertThat(namesWithUuid(uuid), is(List.of()));
    }

    @Test
    @TestSecurity(user = "alice")
    void shouldRejectDeletingABuiltInPreset() {
        final var japanUuid = presetNamed(listPresets(), "Japan").uuid().toString();

        given().when().delete("/api/budget/presets/" + japanUuid)
                .then().statusCode(400)
                .body("messageCode", equalTo("messages.errors.salaryPresetBuiltIn"));

        // The built-in survives the rejected delete.
        assertThat(namesWithUuid(japanUuid), contains("Japan"));
    }

    @Test
    @TestSecurity(user = "alice")
    void shouldRejectDeletingAMissingPreset() {
        given().when().delete("/api/budget/presets/" + UUID.randomUUID())
                .then().statusCode(400)
                .body("messageCode", equalTo("messages.errors.salaryPresetNotFound"));
    }

    // ---------- service-layer reads (the list body the mock can't stream) ----------

    private List<SalaryPresetView> listPresets() {
        return QuarkusTransaction.requiringNew().call(salaryPresetService::list);
    }

    /** The names of presets carrying the given uuid (one if present, empty if not). */
    private List<String> namesWithUuid(String uuid) {
        return listPresets().stream()
                .filter(preset -> uuid.equals(preset.uuid().toString()))
                .map(SalaryPresetView::name)
                .toList();
    }

    private static SalaryPresetView presetNamed(List<SalaryPresetView> presets, String name) {
        return presets.stream().filter(preset -> name.equals(preset.name())).findFirst().orElseThrow();
    }

    private static BudgetMonthView.DeductionView deductionNamed(SalaryPresetView preset, String label) {
        return preset.salary().deductions().stream()
                .filter(deduction -> label.equals(deduction.label())).findFirst().orElseThrow();
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
