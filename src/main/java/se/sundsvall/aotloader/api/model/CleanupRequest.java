package se.sundsvall.aotloader.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Source;

/**
 * What a cleanup is to remove. A field it does not have, or an empty list, is refused.
 *
 * @param dryRun  true for a dry run, which only reads and reports what it would remove
 * @param sources the sources whose items to clean up, all when left out
 * @param kinds   the kinds of item to clean up, all when left out
 * @param limit   the largest number of items the cleanup takes, no limit when left out
 */
@Schema(description = "What a cleanup is to remove from the target systems")
public record CleanupRequest(
	@Schema(description = "A dry run only reads and reports what it would remove. A cleanup is a dry run unless this says false.", defaultValue = "true") Boolean dryRun,
	@Schema(description = "The sources whose items to clean up, all when left out") @Size(min = 1) List<@NotNull Source> sources,
	@Schema(description = "The kinds of item to clean up, all when left out") @Size(min = 1) List<@NotNull Kind> kinds,
	@Schema(description = "The largest number of items the cleanup takes, no limit when left out") @Min(1) Integer limit)
	implements
	RejectsUnknownFields {

	/**
	 * @return false only when the request says so
	 */
	public boolean dryRunOrDefault() {
		return !Boolean.FALSE.equals(dryRun);
	}
}
