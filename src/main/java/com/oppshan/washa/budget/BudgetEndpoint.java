package com.oppshan.washa.budget;

import com.oppshan.washa.auth.UserSessionManager;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

/**
 * The budget app's API (shared household dataset). Thin: parses inputs and delegates to services.
 */
@Path("/api/budget")
@Authenticated
@ApplicationScoped
public class BudgetEndpoint {

    private final BudgetService budgetService;
    private final FxService fxService;
    private final UserSessionManager userSessionManager;

    @Inject
    public BudgetEndpoint(BudgetService budgetService,
                          FxService fxService,
                          UserSessionManager userSessionManager) {
        this.budgetService = budgetService;
        this.fxService = fxService;
        this.userSessionManager = userSessionManager;
    }

    @GET
    @Path("/month/{yearMonth}")
    @Produces(MediaType.APPLICATION_JSON)
    public BudgetMonthView getMonth(@PathParam("yearMonth") String yearMonth) {
        return budgetService.getMonth(YearMonth.parse(yearMonth));
    }

    @PUT
    @Path("/month/{yearMonth}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public BudgetMonthView saveMonth(@PathParam("yearMonth") String yearMonth,
                                     BudgetMonthView month) {
        final var parsed = YearMonth.parse(yearMonth);
        budgetService.saveMonth(parsed, month, userSessionManager.sessionUserAccount().uuid());
        return budgetService.getMonth(parsed);
    }

    @POST
    @Path("/compute")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ComputedView compute(BudgetMonthView month,
                                @QueryParam("month") String asOf) {
        return asOf == null
                ? budgetService.compute(month)
                : budgetService.compute(month, YearMonth.parse(asOf));
    }

    @GET
    @Path("/fx")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, BigDecimal> fx(@QueryParam("base") @DefaultValue("JPY") String base) {
        return fxService.rates(base);
    }

    @PUT
    @Path("/fx")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, BigDecimal> setFx(@Valid FxRateRequest request) {
        return fxService.setRate(request.base(), request.quote(), request.rate());
    }

    /** Upsert request: the base/quote pair and the new rate (units of quote per one base). */
    @RegisterForReflection
    public record FxRateRequest(
            @NotEmpty String base,
            @NotEmpty String quote,
            @NotNull @Positive BigDecimal rate) {
    }
}
