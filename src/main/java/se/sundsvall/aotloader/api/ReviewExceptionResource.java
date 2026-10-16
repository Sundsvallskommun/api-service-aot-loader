package se.sundsvall.aotloader.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.sundsvall.aotloader.api.model.ReviewException;
import se.sundsvall.aotloader.api.model.ReviewExceptionRequest;
import se.sundsvall.aotloader.service.MigrationService;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.dept44.common.validators.annotation.ValidMunicipalityId;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;

import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;
import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;
import static se.sundsvall.aotloader.api.MigrationResource.SOURCE_ID_PATTERN;

@RestController
@Validated
@RequestMapping("/{municipalityId}/migrations/exceptions")
@Tag(name = "Exception list", description = "What the business decided for items that cannot be corrected in the source (section 7.1.1)")
@ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(oneOf = {
	Problem.class, ConstraintViolationProblem.class
})))
@ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
class ReviewExceptionResource {

	private final MigrationService service;

	ReviewExceptionResource(final MigrationService service) {
		this.service = service;
	}

	@GetMapping(produces = APPLICATION_JSON_VALUE)
	@Operation(summary = "List the exception list", responses = @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true))
	ResponseEntity<List<ReviewException>> getExceptions(
		@Parameter(name = "municipalityId", description = "Municipality ID", example = "2281") @ValidMunicipalityId @PathVariable final String municipalityId) {

		return ok(service.getExceptions());
	}

	@PutMapping(path = "/{source}/{kind}/{sourceId}", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	@Operation(summary = "Put an item on the exception list, or change what the list says about it", description = """
		The exception lifts the review rules that the business may decide about. A rule that the migration cannot do without, \
		e.g. a permit holder missing in Party, keeps the item for review until the source is corrected.""", responses = @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true))
	ResponseEntity<ReviewException> putException(
		@Parameter(name = "municipalityId", description = "Municipality ID", example = "2281") @ValidMunicipalityId @PathVariable final String municipalityId,
		@Parameter(name = "source", description = "The source") @PathVariable final Source source,
		@Parameter(name = "kind", description = "The kind of item") @PathVariable final Kind kind,
		@Parameter(name = "sourceId", description = "The id of the item in the source", example = "1234") @Pattern(regexp = SOURCE_ID_PATTERN) @PathVariable final String sourceId,
		@Valid @RequestBody final ReviewExceptionRequest request) {

		return ok(service.putException(new ItemKey(source, kind, sourceId), request));
	}

	@DeleteMapping(path = "/{source}/{kind}/{sourceId}", produces = ALL_VALUE)
	@Operation(summary = "Take an item off the exception list", responses = {
		@ApiResponse(responseCode = "204", description = "No Content", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "404", description = "Not Found", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
	})
	ResponseEntity<Void> deleteException(
		@Parameter(name = "municipalityId", description = "Municipality ID", example = "2281") @ValidMunicipalityId @PathVariable final String municipalityId,
		@Parameter(name = "source", description = "The source") @PathVariable final Source source,
		@Parameter(name = "kind", description = "The kind of item") @PathVariable final Kind kind,
		@Parameter(name = "sourceId", description = "The id of the item in the source", example = "1234") @Pattern(regexp = SOURCE_ID_PATTERN) @PathVariable final String sourceId) {

		service.deleteException(new ItemKey(source, kind, sourceId));
		return noContent().build();
	}
}
