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
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.sundsvall.aotloader.api.model.MigrationItem;
import se.sundsvall.aotloader.api.model.ReportLine;
import se.sundsvall.aotloader.api.model.Run;
import se.sundsvall.aotloader.api.model.RunRequest;
import se.sundsvall.aotloader.service.MigrationService;
import se.sundsvall.aotloader.service.ReportCsv;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.ItemStatus;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.dept44.common.validators.annotation.ValidMunicipalityId;
import se.sundsvall.dept44.common.validators.annotation.ValidUuid;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;

import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.LOCATION;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;
import static org.springframework.http.ResponseEntity.accepted;
import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.web.util.UriComponentsBuilder.fromPath;

@RestController
@Validated
@RequestMapping("/{municipalityId}/migrations")
@Tag(name = "Migration", description = "Runs the migration of permits, notifications and inspection errands from AlkT and AlkOL")
@ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(oneOf = {
	Problem.class, ConstraintViolationProblem.class
})))
@ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
class MigrationResource {

	static final String SOURCE_ID_PATTERN = "^\\d{1,10}(-[A-Z_]{1,30})?$";
	static final String TEXT_CSV_VALUE = "text/csv";

	private final MigrationService service;

	MigrationResource(final MigrationService service) {
		this.service = service;
	}

	@PostMapping(path = "/runs", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	@Operation(summary = "Start a run", description = """
		Starts a run in the background and answers at once. A run is a dry run unless the request says dryRun false: it reads, applies \
		the review rules, looks up the permit holders in Party and checks the files, and writes nothing to the target systems. \
		Starting a run again never migrates an item twice: an item already migrated is left as it is, and an item that stopped half \
		way continues from where it stopped. Only one run at a time.""", responses = {
		@ApiResponse(responseCode = "202", description = "Accepted - the run has started", headers = @Header(name = LOCATION, description = "The run"), useReturnTypeSchema = true),
		@ApiResponse(responseCode = "409", description = "Conflict - another run is running", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	})
	ResponseEntity<Run> startRun(
		@Parameter(name = "municipalityId", description = "Municipality ID", example = "2281") @ValidMunicipalityId @PathVariable final String municipalityId,
		@Valid @RequestBody final RunRequest request) {

		final var run = service.startRun(municipalityId, request);
		return accepted()
			.location(fromPath("/{municipalityId}/migrations/runs/{runId}").buildAndExpand(municipalityId, run.id()).toUri())
			.body(run);
	}

	@GetMapping(path = "/runs", produces = APPLICATION_JSON_VALUE)
	@Operation(summary = "List the runs, the latest first", responses = @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true))
	ResponseEntity<List<Run>> getRuns(
		@Parameter(name = "municipalityId", description = "Municipality ID", example = "2281") @ValidMunicipalityId @PathVariable final String municipalityId) {

		return ok(service.getRuns(municipalityId));
	}

	@GetMapping(path = "/runs/{runId}", produces = APPLICATION_JSON_VALUE)
	@Operation(summary = "Read a run, with the number of report rows per outcome", responses = {
		@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "404", description = "Not Found", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	})
	ResponseEntity<Run> getRun(
		@Parameter(name = "municipalityId", description = "Municipality ID", example = "2281") @ValidMunicipalityId @PathVariable final String municipalityId,
		@Parameter(name = "runId", description = "The run") @ValidUuid @PathVariable final String runId) {

		return ok(service.getRun(municipalityId, runId));
	}

	@PostMapping(path = "/runs/{runId}/stop", produces = APPLICATION_JSON_VALUE)
	@Operation(summary = "Stop a run before its next item", responses = {
		@ApiResponse(responseCode = "202", description = "Accepted - the run stops before its next item", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "404", description = "Not Found", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class))),
		@ApiResponse(responseCode = "409", description = "Conflict - the run is not running", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	})
	ResponseEntity<Run> stopRun(
		@Parameter(name = "municipalityId", description = "Municipality ID", example = "2281") @ValidMunicipalityId @PathVariable final String municipalityId,
		@Parameter(name = "runId", description = "The run") @ValidUuid @PathVariable final String runId) {

		return accepted().body(service.stopRun(municipalityId, runId));
	}

	@GetMapping(path = "/runs/{runId}/report", produces = APPLICATION_JSON_VALUE)
	@Operation(summary = "Read the report of a run", description = "One row per item, with the review rules and what went wrong. It never holds names or numbers of permit holders.", responses = {
		@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "404", description = "Not Found", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	})
	ResponseEntity<List<ReportLine>> getReport(
		@Parameter(name = "municipalityId", description = "Municipality ID", example = "2281") @ValidMunicipalityId @PathVariable final String municipalityId,
		@Parameter(name = "runId", description = "The run") @ValidUuid @PathVariable final String runId,
		@Parameter(name = "outcome", description = "The outcomes to list, all when left out") @RequestParam(name = "outcome", required = false) final List<Outcome> outcomes) {

		return ok(service.getReport(municipalityId, runId, outcomes));
	}

	@GetMapping(path = "/runs/{runId}/report.csv", produces = TEXT_CSV_VALUE)
	@Operation(summary = "Read the report of a run as a CSV file for Excel", responses = {
		@ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = TEXT_CSV_VALUE, schema = @Schema(type = "string"))),
		@ApiResponse(responseCode = "404", description = "Not Found", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	})
	ResponseEntity<byte[]> getReportCsv(
		@Parameter(name = "municipalityId", description = "Municipality ID", example = "2281") @ValidMunicipalityId @PathVariable final String municipalityId,
		@Parameter(name = "runId", description = "The run") @ValidUuid @PathVariable final String runId,
		@Parameter(name = "outcome", description = "The outcomes to list, all when left out") @RequestParam(name = "outcome", required = false) final List<Outcome> outcomes) {

		final var csv = ReportCsv.write(service.getReport(municipalityId, runId, outcomes));
		return ok()
			.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
			.header(CONTENT_DISPOSITION, ContentDisposition.attachment().filename("rapport-" + runId + ".csv").build().toString())
			.body(csv.getBytes(StandardCharsets.UTF_8));
	}

	@PostMapping(path = "/items/{source}/{kind}/{sourceId}", produces = APPLICATION_JSON_VALUE)
	@Operation(summary = "Migrate a single item, or try it in a dry run", description = """
		Runs one item through the same flow as a full run and waits for it. The id is ObjektID in AlkT or FSID in AlkOL \
		for a permit, FSID and product for a notification, e.g. 1234-FOLKOL, ArendeID for an inspection errand and \
		TillsynsTillfalleNr for an inspection visit. An inspection errand waits until the permit of its place is migrated.""", responses = {
		@ApiResponse(responseCode = "200", description = "OK - the row of the report for the item", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "404", description = "Not Found - the source has no such item", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class))),
		@ApiResponse(responseCode = "409", description = "Conflict - a run is running", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class))),
		@ApiResponse(responseCode = "422",
			description = "Unprocessable - the checks before the run failed, the source could not be read, or the run was stopped",
			content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE,
				schema = @Schema(
					implementation = Problem.class)))
	})
	ResponseEntity<ReportLine> migrateItem(
		@Parameter(name = "municipalityId", description = "Municipality ID", example = "2281") @ValidMunicipalityId @PathVariable final String municipalityId,
		@Parameter(name = "source", description = "The source") @PathVariable final Source source,
		@Parameter(name = "kind", description = "The kind of item") @PathVariable final Kind kind,
		@Parameter(name = "sourceId", description = "The id of the item in the source", example = "1234") @Pattern(regexp = SOURCE_ID_PATTERN) @PathVariable final String sourceId,
		@Parameter(name = "dryRun", description = "A dry run writes nothing to the target systems") @RequestParam(name = "dryRun", defaultValue = "true") final boolean dryRun) {

		return ok(service.migrateItem(municipalityId, new ItemKey(source, kind, sourceId), dryRun));
	}

	@GetMapping(path = "/items", produces = APPLICATION_JSON_VALUE)
	@Operation(summary = "List the status table", responses = @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true))
	ResponseEntity<List<MigrationItem>> getItems(
		@Parameter(name = "municipalityId", description = "Municipality ID", example = "2281") @ValidMunicipalityId @PathVariable final String municipalityId,
		@Parameter(name = "source", description = "Only items of the source") @RequestParam(name = "source", required = false) final Source source,
		@Parameter(name = "kind", description = "Only items of the kind") @RequestParam(name = "kind", required = false) final Kind kind,
		@Parameter(name = "status", description = "Only items with the status") @RequestParam(name = "status", required = false) final ItemStatus status) {

		return ok(service.getItems(source, kind, status));
	}

	@GetMapping(path = "/items/{source}/{kind}/{sourceId}", produces = APPLICATION_JSON_VALUE)
	@Operation(summary = "Read an item of the status table", responses = {
		@ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "404", description = "Not Found", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	})
	ResponseEntity<MigrationItem> getItem(
		@Parameter(name = "municipalityId", description = "Municipality ID", example = "2281") @ValidMunicipalityId @PathVariable final String municipalityId,
		@Parameter(name = "source", description = "The source") @PathVariable final Source source,
		@Parameter(name = "kind", description = "The kind of item") @PathVariable final Kind kind,
		@Parameter(name = "sourceId", description = "The id of the item in the source", example = "1234") @Pattern(regexp = SOURCE_ID_PATTERN) @PathVariable final String sourceId) {

		return ok(service.getItem(new ItemKey(source, kind, sourceId)));
	}
}
