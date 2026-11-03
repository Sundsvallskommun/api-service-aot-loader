package se.sundsvall.aotloader.service.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import static java.util.Objects.requireNonNull;
import static se.sundsvall.aotloader.Constants.DECISION_OUTCOME_APPROVAL;
import static se.sundsvall.aotloader.Constants.DECISION_OUTCOME_APPROVAL_WITH_CONDITIONS;

/**
 * The decision a permit was granted by, which the errand of the permit gets as a completed manual decision (decision
 * 70). Holds names and the JSON of the permit, which names the holder, in memory only, and {@link #toString()} shows
 * none of them.
 *
 * @param decidedBy     who made the decision: the name of the decision maker in the source, or else the code the source
 *                      names them with, or a text that says the source lacks them
 * @param maker         the name or code of the decision maker as the source gives it, null when the source names nobody
 * @param decidedAt     when the decision was made
 * @param description   the type of the decision in the source, null when unknown
 * @param justification the text of the decision in the source, null when it has none
 * @param validFrom     the first day the decision is valid, null when the source does not say
 * @param validTo       the last day the decision is valid, null when it is valid until further notice
 * @param terms         the conditions of the permit, in order; with any, the decision is an approval with conditions
 * @param documents     the files of the decision, which the decision links to among the attachments of the errand
 * @param caseNumber    the diary number of the errand the decision was made in, null when unknown
 * @param caseOfficer   the handlers of the errand the decision was made in, null when unknown
 * @param content       what the decision says of the permit: its JSON parameter by the decision schema of the area, as
 *                      the source gives it (decision 92), without what is added when it is written, like the JSON
 *                      parameter of the post in PartyAssets; empty until the post is built
 */
public record DecisionData(
	String decidedBy,
	String maker,
	LocalDateTime decidedAt,
	String description,
	String justification,
	LocalDate validFrom,
	LocalDate validTo,
	List<String> terms,
	List<FileReference> documents,
	String caseNumber,
	String caseOfficer,
	ObjectNode content) {

	public DecisionData {
		requireNonNull(decidedBy, "decidedBy");
		requireNonNull(decidedAt, "decidedAt");
		terms = terms == null ? List.of() : List.copyOf(terms);
		documents = documents == null ? List.of() : List.copyOf(documents);
		content = content == null ? JsonNodeFactory.instance.objectNode() : content.deepCopy();
	}

	/**
	 * A decision without case and content.
	 */
	public DecisionData(final String decidedBy, final String maker, final LocalDateTime decidedAt, final String description, final String justification, final LocalDate validFrom,
		final LocalDate validTo, final List<String> terms, final List<FileReference> documents) {
		this(decidedBy, maker, decidedAt, description, justification, validFrom, validTo, terms, documents, null, null, null);
	}

	/**
	 * @return a copy of what the decision says of the permit, which the caller may change
	 */
	@Override
	public ObjectNode content() {
		return content.deepCopy();
	}

	/**
	 * @return true when the decision says something of the permit
	 */
	public boolean hasContent() {
		return !content.isEmpty();
	}

	/**
	 * @param  number   the diary number of the errand the decision was made in, null when unknown
	 * @param  officers the handlers of that errand, null when unknown
	 * @return          the decision as made in that errand
	 */
	public DecisionData withCase(final String number, final String officers) {
		return new DecisionData(decidedBy, maker, decidedAt, description, justification, validFrom, validTo, terms, documents, number, officers, content);
	}

	/**
	 * @return the decision with this as what it says of the permit
	 */
	public DecisionData withContent(final ObjectNode value) {
		return new DecisionData(decidedBy, maker, decidedAt, description, justification, validFrom, validTo, terms, documents, caseNumber, caseOfficer, value);
	}

	/**
	 * @return an approval, with conditions when the decision has terms, as Draken gives it the outcome
	 */
	public String outcome() {
		return terms.isEmpty() ? DECISION_OUTCOME_APPROVAL : DECISION_OUTCOME_APPROVAL_WITH_CONDITIONS;
	}

	@Override
	public String toString() {
		return "DecisionData[decidedAt=" + decidedAt + ", terms=" + terms.size() + ", documents=" + documents.size() + ", content=" + content.propertyNames() + "]";
	}
}
