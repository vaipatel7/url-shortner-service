package com.urlshortener.resource.v1;

import com.urlshortener.dto.v1.AnalyticsResponse;
import com.urlshortener.dto.v1.DeviceDistributionResponse;
import com.urlshortener.dto.v1.ErrorResponse;
import com.urlshortener.dto.v1.TopUrlsResponse;
import com.urlshortener.service.v1.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST resource exposing click analytics.
 *
 * GET /v1/analytics?page=0      – paginated 7-day analytics windows
 * GET /v1/analytics/devices     – device-type distribution for the last 30 days
 * GET /v1/analytics/top-urls    – top 10 URLs by clicks for today (UTC)
 */
@Tag(name = "Analytics", description = "Click analytics and device distribution")
@Path("/v1/analytics")
@Produces(MediaType.APPLICATION_JSON)
public class AnalyticsResource {

    private final AnalyticsService analyticsService;

    public AnalyticsResource(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Operation(summary = "Get analytics for a 7-day window",
            description = "Returns click analytics for a selected 7-day window. page=0 is the most recent; page=1 is the preceding one, etc.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analytics for the selected window",
                    content = @Content(schema = @Schema(implementation = AnalyticsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid page parameter",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GET
    public Response getAnalytics(
            @Parameter(description = "Zero-based window index (0 = last 7 days)", example = "0")
            @QueryParam("page") @DefaultValue("0") int page) {

        if (page < 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.builder().code(400).message("page must be 0 or greater").build())
                    .build();
        }
        return Response.ok(analyticsService.getAnalytics(page)).build();
    }

    @Operation(summary = "Get device-type distribution",
            description = "Returns click counts grouped by device type (DESKTOP, MOBILE, TABLET, BOT, UNKNOWN) for the last 30 days.")
    @ApiResponse(responseCode = "200", description = "Device distribution",
            content = @Content(schema = @Schema(implementation = DeviceDistributionResponse.class)))
    @GET
    @Path("/devices")
    public Response getDeviceDistribution() {
        DeviceDistributionResponse body = DeviceDistributionResponse.builder()
                .data(analyticsService.getDeviceTypeDistribution())
                .build();
        return Response.ok(body).build();
    }

    @Operation(summary = "Get top 10 URLs for today",
            description = "Returns the top 10 short URLs ranked by click count for today (UTC calendar day).")
    @ApiResponse(responseCode = "200", description = "Top URLs for today",
            content = @Content(schema = @Schema(implementation = TopUrlsResponse.class)))
    @GET
    @Path("/top-urls")
    public Response getTopUrlsToday() {
        TopUrlsResponse body = TopUrlsResponse.builder()
                .data(analyticsService.getTopUrlsToday())
                .build();
        return Response.ok(body).build();
    }
}
