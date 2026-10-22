package se.sundsvall.aotloader.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.sundsvall.aotloader.api.model.CleanupRequest;
import se.sundsvall.aotloader.api.model.ReportLine;
import se.sundsvall.aotloader.api.model.Run;
import se.sundsvall.aotloader.service.MigrationService;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.dept44.common.validators.annotation.ValidMunicipalityId;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;

import static org.springframework.http.HttpHeaders.LOCATION;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;
import static org.springframework.http.ResponseEntity.accepted;
import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.web.util.UriComponentsBuilder.fromPath;
import static se.sundsvall.aotloader.api.MigrationResource.SOURCE_ID_PATTERN;

/**
 * Removes what the migration wrote to the target systems of the environment, so that it can be migrated again (section
 * 10.3). Only there when the setting aot-loader.cleanup.enabled is true, which is never in production.
 */
@RestController
@Validated
@ConditionalOnProperty(name = "aot-loader.cleanup.enabled", havingValue = "true")
@RequestMapping("/{municipalityId}/migrations")
@Tag(name = "Cleanup", description = "Removes migrated errands, their permits in PartyAssets and their relations from a test environment")
@ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(oneOf = {
	Problem.class, ConstraintViolationProblem.class
})))
@ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
class CleanupResource {

	private final MigrationService service;

	CleanupResource(final MigrationService service) {
		this.service = service;
	}

	@PostMapping(path = "/cleanups", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	@Operation(summary = "Start a cleanup", description = """
		Starts a cleanup in the background and answers at once. For every item of the status table in the selection it removes \
		the relations the migration and PartyAssets wrote from its errand, its permit in PartyAssets and its errand in \
		SupportManagement, and then the item, so that the next run migrates it anew. A permit is removed only when its asset \
		id is the errand's, and an errand only when it carries the item's tag migreringsid. A cleanup is a dry run unless the \
		request says dryRun false, and then only reads and reports what it would remove. It is followed and stopped as a run, \
		and its report has the outcomes RENSAD, SKULLE_RENSAS and FEL. Only one run or cleanup at a time.""", responses = {
		@ApiResponse(responseCode = "202", description = "Accepted - the cleanup has started", headers = @Header(name = LOCATION, description = "The cleanup, as a run"), useReturnTypeSchema = true),
		@ApiResponse(responseCode = "409", description = "Conflict - another run is running", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	})
	ResponseEntity<Run> startCleanup(
		@Parameter(name = "municipalityId", description = "Municipality ID", example = "2281") @ValidMunicipalityId @PathVariable final String municipalityId,
		@Valid @RequestBody final CleanupRequest request) {

		final var run = service.startCleanup(municipalityId, request);
		return accepted()
			.location(fromPath("/{municipalityId}/migrations/runs/{runId}").buildAndExpand(municipalityId, run.id()).toUri())
			.body(run);
	}

	@PostMapping(path = "/items/{source}/{kind}/{sourceId}/cleanup", produces = APPLICATION_JSON_VALUE)
	@Operation(summary = "Clean up a single item, or try it in a dry run", description = """
		Removes what the migration wrote for one item of the status table, as a cleanup does, and waits for it.""", responses = {
		@ApiResponse(responseCode = "200", description = "OK - the row of the report for the item", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "404", description = "Not Found - the status table has no such item", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class))),
		@ApiResponse(responseCode = "409", description = "Conflict - a run is running", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class))),
		@ApiResponse(responseCode = "422", description = "Unprocessable - the cleanup did not start or failed", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	})
	ResponseEntity<ReportLine> cleanupItem(
		@Parameter(name = "municipalityId", description = "Municipality ID", example = "2281") @ValidMunicipalityId @PathVariable final String municipalityId,
		@Parameter(name = "source", description = "The source") @PathVariable final Source source,
		@Parameter(name = "kind", description = "The kind of item") @PathVariable final Kind kind,
		@Parameter(name = "sourceId", description = "The id of the item in the source", example = "1234") @Pattern(regexp = SOURCE_ID_PATTERN) @PathVariable final String sourceId,
		@Parameter(name = "dryRun", description = "A dry run removes nothing") @RequestParam(name = "dryRun", defaultValue = "true") final boolean dryRun) {

		return ok(service.cleanupItem(municipalityId, new ItemKey(source, kind, sourceId), dryRun));
	}
}
