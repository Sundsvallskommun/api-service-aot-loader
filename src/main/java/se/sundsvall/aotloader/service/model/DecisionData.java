package se.sundsvall.aotloader.service.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static se.sundsvall.aotloader.Constants.DECISION_OUTCOME_APPROVAL;
import static se.sundsvall.aotloader.Constants.DECISION_OUTCOME_APPROVAL_WITH_CONDITIONS;

/**
 * The decision a permit was granted by, which the errand of the permit gets as a completed manual decision (decision
 * 70). Holds names and the parameters of the certificate, which name the holder, in memory only, and
 * {@link #toString()} shows none of them.
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
 * @param decided       what the permit was granted with, as the parameters Draken writes and reads on a decision, such
 *                      as the beverages and the serving hours (decision 76)
 * @param caseNumber    the diary number of the errand the decision was made in, null when unknown
 * @param caseOfficer   the handlers of the errand the decision was made in, null when unknown
 * @param certificate   the parameters Draken gives the decision for the certificate of the permit, by their keys,
 *                      but the text of the outcome, which the namespace names (decision 87)
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
	List<Parameter> decided,
	String caseNumber,
	String caseOfficer,
	Map<String, String> certificate) {

	public DecisionData {
		requireNonNull(decidedBy, "decidedBy");
		requireNonNull(decidedAt, "decidedAt");
		terms = terms == null ? List.of() : List.copyOf(terms);
		documents = documents == null ? List.of() : List.copyOf(documents);
		decided = decided == null ? List.of() : List.copyOf(decided);
		certificate = certificate == null ? Map.of() : unmodifiableMap(new LinkedHashMap<>(certificate));
	}

	/**
	 * A decision without decided parameters, case and certificate.
	 */
	public DecisionData(final String decidedBy, final String maker, final LocalDateTime decidedAt, final String description, final String justification, final LocalDate validFrom,
		final LocalDate validTo, final List<String> terms, final List<FileReference> documents) {
		this(decidedBy, maker, decidedAt, description, justification, validFrom, validTo, terms, documents, List.of(), null, null, Map.of());
	}

	/**
	 * @return the decision with these decided parameters
	 */
	public DecisionData withDecided(final List<Parameter> parameters) {
		return new DecisionData(decidedBy, maker, decidedAt, description, justification, validFrom, validTo, terms, documents, parameters, caseNumber, caseOfficer, certificate);
	}

	/**
	 * @param  number   the diary number of the errand the decision was made in, null when unknown
	 * @param  officers the handlers of that errand, null when unknown
	 * @return          the decision as made in that errand
	 */
	public DecisionData withCase(final String number, final String officers) {
		return new DecisionData(decidedBy, maker, decidedAt, description, justification, validFrom, validTo, terms, documents, decided, number, officers, certificate);
	}

	/**
	 * @return the decision with these parameters of the certificate
	 */
	public DecisionData withCertificate(final Map<String, String> parameters) {
		return new DecisionData(decidedBy, maker, decidedAt, description, justification, validFrom, validTo, terms, documents, decided, caseNumber, caseOfficer, parameters);
	}

	/**
	 * @return an approval, with conditions when the decision has terms, as Draken gives it the outcome
	 */
	public String outcome() {
		return terms.isEmpty() ? DECISION_OUTCOME_APPROVAL : DECISION_OUTCOME_APPROVAL_WITH_CONDITIONS;
	}

	@Override
	public String toString() {
		return "DecisionData[decidedAt=" + decidedAt + ", terms=" + terms.size() + ", documents=" + documents.size() + ", decided=" + decided.size() + ", certificate="
			+ certificate.keySet() + "]";
	}
}
