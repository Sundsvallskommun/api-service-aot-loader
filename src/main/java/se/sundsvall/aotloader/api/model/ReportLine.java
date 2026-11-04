package se.sundsvall.aotloader.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.Source;

/**
 * A row of the report of a run: what the run did with one source item. It never holds names or numbers of permit
 * holders or other persons.
 *
 * @param source       the source
 * @param kind         the kind of item
 * @param sourceId     the id of the item in the source
 * @param placeNumber  the restaurant number of the place, so the business can find it in the old system
 * @param placeName    the name of the place
 * @param outcome      what the run did
 * @param rules        the codes of the review rules that hit the item, comma separated
 * @param detail       what the business needs to know about the item
 * @param errandNumber the number of the errand in SupportManagement
 */
@Schema(description = "A row of the report of a run")
public record ReportLine(
	Source source,
	Kind kind,
	String sourceId,
	String placeNumber,
	String placeName,
	Outcome outcome,
	String rules,
	String detail,
	String errandNumber) {
}
