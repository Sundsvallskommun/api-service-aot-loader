package se.sundsvall.aotloader.service.model;

import java.util.List;

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

	public Person {
		positions = positions == null ? List.of() : List.copyOf(positions);
	}

	/**
	 * @return true when the number is an organization number
	 */
	public boolean isCompany() {
		return legalId != null && legalId.type() == PartyType.ENTERPRISE;
	}

	@Override
	public String toString() {
		return "Person[legalId=" + legalId + "]";
	}
}
