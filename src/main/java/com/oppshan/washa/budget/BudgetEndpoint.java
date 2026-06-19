package com.oppshan.washa.budget;

import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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

    @Inject
    public BudgetEndpoint(BudgetService budgetService, FxService fxService) {
        this.budgetService = budgetService;
        this.fxService = fxService;
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
    public BudgetMonthView saveMonth(@PathParam("yearMonth") String yearMonth, BudgetMonthView month) {
        final var parsed = YearMonth.parse(yearMonth);
        budgetService.saveMonth(parsed, month, null);
        return budgetService.getMonth(parsed);
    }

    @POST
    @Path("/compute")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ComputedView compute(BudgetMonthView month) {
        return budgetService.compute(month);
    }

    @GET
    @Path("/fx")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, BigDecimal> fx(@QueryParam("base") @DefaultValue("JPY") String base) {
        return fxService.rates(base);
    }
}
