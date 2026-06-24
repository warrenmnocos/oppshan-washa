package com.oppshan.washa.budget;

import com.oppshan.washa.budget.BudgetMonthView.SalaryView;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

/**
 * The salary-preset API (shared household preset store). Thin: delegates to {@link SalaryPresetService}.
 * GET lists every preset (built-ins first), POST saves a user preset (201), DELETE removes one (204).
 */
@Path("/api/budget/presets")
@Authenticated
@ApplicationScoped
public class SalaryPresetEndpoint {

    private final SalaryPresetService salaryPresetService;

    @Inject
    public SalaryPresetEndpoint(SalaryPresetService salaryPresetService) {
        this.salaryPresetService = salaryPresetService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<SalaryPresetView> list() {
        return salaryPresetService.list();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(@Valid SalaryPresetRequest request) {
        final var created = salaryPresetService.create(request.name(), request.salary());
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @DELETE
    @Path("/{uuid}")
    public Response delete(@PathParam("uuid") UUID uuid) {
        salaryPresetService.delete(uuid);
        return Response.noContent().build();
    }

    /** Create request: a name plus the salary payload (the dialog's working draft). */
    @RegisterForReflection
    public record SalaryPresetRequest(
            @NotEmpty String name,
            @Valid @NotNull SalaryView salary) {
    }
}
