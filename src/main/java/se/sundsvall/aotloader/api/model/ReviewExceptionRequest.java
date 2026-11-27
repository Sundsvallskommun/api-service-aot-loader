package se.sundsvall.aotloader.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import se.sundsvall.aotloader.service.model.ExceptionOutcome;

/**
 * What the business decided for an item. A field it does not have is refused.
 *
 * @param outcome   what was decided
 * @param decidedBy who decided
 * @param decidedAt when it was decided, today when left out
 * @param comment   why, never names or numbers of permit holders
 */
@Schema(description = "What the business decided for an item that cannot be corrected in the source")
public record ReviewExceptionRequest(
	@Schema(description = "MIGRERA to migrate the item despite the rules that hit it, MIGRERA_SOM_UTGANGET to migrate it with the permit as EXPIRED") @NotNull ExceptionOutcome outcome,
	@Schema(description = "Who decided, e.g. a user id") @NotBlank @Size(max = 100) String decidedBy,
	@Schema(description = "When it was decided, today when left out") LocalDate decidedAt,
	@Schema(description = "Why; no names or numbers of permit holders") @Size(max = 1000) String comment)
	implements
	RejectsUnknownFields {
}
