package se.sundsvall.aotloader.service.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * The decision a permit was granted by, which the errand of the permit gets as a completed manual decision (decision
 * 70). Holds the name of the decision maker in memory only, and {@link #toString()} shows none of it.
 *
 * @param decidedBy     who made the decision: the name of the decision maker in the source, or else the code the source
 *                      names them with
 * @param decidedAt     when the decision was made
 * @param description   the type of the decision in the source, null when unknown
 * @param justification the text of the decision in the source, null when it has none
 * @param validFrom     the first day the decision is valid, null when the source does not say
 * @param validTo       the last day the decision is valid, null when it is valid until further notice
 * @param terms         the conditions of the permit, in order; with any, the decision is an approval with conditions
 * @param documents     the files of the decision, which the decision links to among the attachments of the errand
 */
public record DecisionData(
	String decidedBy,
	LocalDateTime decidedAt,
	String description,
	String justification,
	LocalDate validFrom,
	LocalDate validTo,
	List<String> terms,
	List<FileReference> documents) {

	public DecisionData {
		requireNonNull(decidedBy, "decidedBy");
		requireNonNull(decidedAt, "decidedAt");
		terms = terms == null ? List.of() : List.copyOf(terms);
		documents = documents == null ? List.of() : List.copyOf(documents);
	}

	@Override
	public String toString() {
		return "DecisionData[decidedAt=" + decidedAt + ", terms=" + terms.size() + ", documents=" + documents.size() + "]";
	}
}
