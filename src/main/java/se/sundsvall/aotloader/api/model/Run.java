package se.sundsvall.aotloader.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.RunStatus;

/**
 * A run of the migration, or of a cleanup.
 *
 * @param id             the id of the run
 * @param municipalityId the municipality
 * @param dryRun         true for a dry run
 * @param cleanup        true for a cleanup, which removes what the migration wrote to the target systems
 * @param sources        the sources read
 * @param kinds          the kinds migrated or cleaned up
 * @param itemKey        the single item of the run, null for a full run
 * @param limit          the largest number of items written, null for no limit
 * @param status         the status of the run
 * @param message        what the run has to say, e.g. why it failed or what the checks before it found
 * @param started        when the run started
 * @param finished       when the run finished, null while it runs
 * @param outcomes       the number of report rows per outcome
 */
@Schema(description = "A run of the migration, or of a cleanup")
public record Run(
	String id,
	String municipalityId,
	boolean dryRun,
	boolean cleanup,
	List<String> sources,
	List<String> kinds,
	String itemKey,
	Integer limit,
	RunStatus status,
	String message,
	OffsetDateTime started,
	OffsetDateTime finished,
	Map<Outcome, Long> outcomes) {
}
