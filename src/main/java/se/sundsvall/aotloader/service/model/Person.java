package se.sundsvall.aotloader.service.model;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A person with significant influence over the permit holder (decision 15), which can also be a company, e.g. an owning
 * company. It is personal data: it is kept in memory only, and {@link #toString()} shows none of it.
 *
 * @param name      the name as the source has it
 * @param legalId   the personal identity number, or the organization number of a company; null when the number is
 *                  missing or cannot be used
 * @param foreign   true for a person the source marks as without a Swedish personal identity number, whose missing
 *                  number is expected
 * @param positions the positions of the person, e.g. "Ledamot"
 */
public record Person(String name, LegalId legalId, boolean foreign, List<String> positions) {

	private static final Pattern AUDITOR = Pattern.compile("revisor|revision", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	public Person {
		positions = positions == null ? List.of() : List.copyOf(positions);
	}

	/**
	 * @return true when the number is an organization number
	 */
	public boolean isCompany() {
		return legalId != null && legalId.type() == PartyType.ENTERPRISE;
	}

	/**
	 * @return true when the person has positions and every one of them is that of an auditor, who has no significant
	 *         influence over the business they audit; Draken tells an auditor by the same words (decision 84)
	 */
	public boolean auditorOnly() {
		return !positions.isEmpty() && positions.stream().allMatch(position -> position != null && AUDITOR.matcher(position).find());
	}

	@Override
	public String toString() {
		return "Person[legalId=" + legalId + "]";
	}
}
