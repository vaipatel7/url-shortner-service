package com.urlshortener.resource.v1;

import com.urlshortener.dto.v1.CreateUrlRequest;
import com.urlshortener.dto.v1.CreateUrlResponse;
import com.urlshortener.dto.v1.ErrorResponse;
import com.urlshortener.dto.v1.ShortUrlPageResponse;
import com.urlshortener.dto.v1.ShortUrlSummary;
import com.urlshortener.model.ShortUrl;
import com.urlshortener.service.v1.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST resource for URL shortening and listing.
 *
 * POST /v1/url/create  – create a new short URL
 * GET  /v1/url         – list all short URLs (paginated, 10 per page)
 *
 * Redirection is handled by {@link com.urlshortener.resource.RedirectResource} at /{short_code}.
 */
@Tag(name = "URLs", description = "Short URL management")
@Path("/v1/url")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UrlResource {

    private static final Logger LOG = LoggerFactory.getLogger(UrlResource.class);

    private final UrlService urlService;

    public UrlResource(UrlService urlService) {
        this.urlService = urlService;
    }

    @Operation(summary = "Create a short URL",
            description = "Creates a new short URL. Provide a custom alias (6–20 chars) or omit it for an auto-generated 8-character code.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Short URL created successfully",
                    content = @Content(schema = @Schema(implementation = CreateUrlResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Requested alias is already taken",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @POST
    @Path("/create")
    public Response createUrl(@Valid @NotNull CreateUrlRequest request) {
        if (request.getDomain() != null && !request.getDomain().isBlank()) {
            LOG.warn("Rejected request: custom domain '{}' is not supported", request.getDomain());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.builder()
                            .code(Response.Status.BAD_REQUEST.getStatusCode())
                            .message("custom domains are not supported in this tier")
                            .build())
                    .build();
        }
        ShortUrl shortUrl = urlService.createShortUrl(
                request.getLongUrl(),
                request.getDomain(),
                request.getAlias()
        );

        CreateUrlResponse body = CreateUrlResponse.builder()
                .id(shortUrl.getId())
                .shortCode(shortUrl.getAlias())
                .shortUrl(buildShortUrl(shortUrl.getDomain(), shortUrl.getAlias()))
                .longUrl(shortUrl.getLongUrl())
                .domain(shortUrl.getDomain())
                .aliasType(shortUrl.getAliasType().name())
                .createdAt(shortUrl.getCreatedAt() != null
                        ? shortUrl.getCreatedAt().toString()
                        : Instant.now().toString())
                .build();

        return Response.status(Response.Status.CREATED).entity(body).build();
    }

    @Operation(summary = "List all short URLs (paginated)",
            description = "Returns a paginated list of all short URL records. Page size is fixed at 10.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated list of short URLs",
                    content = @Content(schema = @Schema(implementation = ShortUrlPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid page parameter",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GET
    public Response listAll(
            @Parameter(description = "Zero-based page number", example = "0")
            @QueryParam("page") @DefaultValue("0") int page) {

        if (page < 0) {
            LOG.warn("Invalid page parameter: {}", page);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.builder().code(400).message("page must be 0 or greater").build())
                    .build();
        }

        final int pageSize = 10;
        List<ShortUrlSummary> items = urlService.listAll(page, pageSize)
                .stream()
                .map(u -> ShortUrlSummary.builder()
                        .alias(u.getAlias())
                        .longUrl(u.getLongUrl())
                        .shortUrl(buildShortUrl(u.getDomain(), u.getAlias()))
                        .clickCount(urlService.getClickCount(u.getId()))
                        .build())
                .collect(Collectors.toList());

        long total = urlService.countAll();
        int totalPages = (int) Math.ceil((double) total / pageSize);

        ShortUrlPageResponse body = ShortUrlPageResponse.builder()
                .data(items)
                .page(page)
                .pageSize(pageSize)
                .totalRecords(total)
                .totalPages(totalPages)
                .build();

        return Response.ok(body).build();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String buildShortUrl(String domain, String alias) {
        String protocol = domain.startsWith("localhost") ? "http" : "https";
        return protocol + "://" + domain + "/" + alias;
    }
}
