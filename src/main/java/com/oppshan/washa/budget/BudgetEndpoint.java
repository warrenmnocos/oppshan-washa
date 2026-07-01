package com.oppshan.washa.budget;

import com.oppshan.washa.auth.UserSessionManager;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
 * The budget app's API over the shared household dataset. Thin by design: each method parses its
 * inputs, delegates to a service, and returns the resulting view (no business logic here).
 * {@code @Authenticated} gates every route, so only a signed-in member of the two-person allowlist
 * reaches it.
 */
@Path("/api/budget")
@Authenticated
@ApplicationScoped
public class BudgetEndpoint {

    private final BudgetService budgetService;
    private final FxService fxService;
    private final UserSessionManager userSessionManager;

    /** Injects the budget and FX services plus the session manager that identifies the signed-in user. */
    @Inject
    public BudgetEndpoint(BudgetService budgetService,
                          FxService fxService,
                          UserSessionManager userSessionManager) {
        this.budgetService = budgetService;
        this.fxService = fxService;
        this.userSessionManager = userSessionManager;
    }

    /**
     * Returns the saved month view for {@code yearMonth} (an ISO {@code YYYY-MM}), or an empty month
     * carrying just the currency list when nothing is saved for it yet.
     */
    @GET
    @Path("/month/{yearMonth}")
    @Produces(MediaType.APPLICATION_JSON)
    @Valid
    @NotNull
    public BudgetMonthView getMonth(@PathParam("yearMonth") String yearMonth) {
        return budgetService.getMonth(YearMonth.parse(yearMonth));
    }

    /**
     * Upserts a month (replace-on-conflict) from the posted view, stamping the signed-in user as its
     * last modifier, then returns the reloaded, freshly-mapped view. A PUT rather than a POST: the
     * {@code yearMonth} in the path is the resource key, so re-saving the same month is idempotent.
     */
    @PUT
    @Path("/month/{yearMonth}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Valid
    @NotNull
    public BudgetMonthView saveMonth(@PathParam("yearMonth") String yearMonth,
                                     @Valid @NotNull BudgetMonthView month) {
        final var parsed = YearMonth.parse(yearMonth);
        budgetService.saveMonth(parsed, month, userSessionManager.sessionUserAccount().uuid());
        return budgetService.getMonth(parsed);
    }

    /**
     * Computes live figures for an unsaved draft month without persisting anything. The optional
     * {@code ?month=YYYY-MM} query param is the as-of month: it sets which persisted months count as
     * "before now" when summing each goal's prior balance. Omit it and the draft is treated as having
     * no history, so goals start from zero. POST only because the draft rides in the request body; it
     * creates nothing.
     */
    @POST
    @Path("/compute")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Valid
    @NotNull
    public ComputedView compute(@Valid @NotNull BudgetMonthView month,
                                @QueryParam("month") String asOf) {
        return asOf == null
                ? budgetService.compute(month)
                : budgetService.compute(month, YearMonth.parse(asOf));
    }

    /**
     * Returns the stored FX rates for {@code base} (defaulting to JPY) as a quote→rate map. Falls back
     * to the conservative JPY→PHP planning default when nothing is stored for a JPY base.
     */
    @GET
    @Path("/fx")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, BigDecimal> fx(@QueryParam("base") @DefaultValue("JPY") String base) {
        return fxService.rates(base);
    }

    /**
     * Upserts one base→quote rate and returns the refreshed rate map for that base. A PUT: setting the
     * same pair again just overwrites the stored snapshot.
     */
    @PUT
    @Path("/fx")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, BigDecimal> setFx(@Valid FxRateRequest request) {
        return fxService.setRate(request.base(), request.quote(), request.rate());
    }
}
