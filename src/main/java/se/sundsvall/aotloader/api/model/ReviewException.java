package se.sundsvall.aotloader.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import se.sundsvall.aotloader.service.model.ExceptionOutcome;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Source;

/**
 * A row of the exception list.
 *
 * @param source    the source
 * @param kind      the kind of item
 * @param sourceId  the id of the item in the source
 * @param outcome   what the business decided
 * @param decidedBy who decided
 * @param decidedAt when it was decided
 * @param comment   why
 */
@Schema(description = "A row of the exception list: what the business decided for an item that cannot be corrected in the source")
public record ReviewException(
	Source source,
	Kind kind,
	String sourceId,
	ExceptionOutcome outcome,
	String decidedBy,
	LocalDate decidedAt,
	String comment) {
}
