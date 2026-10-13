package se.sundsvall.aotloader.service.model;

import java.util.List;

/**
 * A person with significant influence over the permit holder (decision 15). It is personal data: it is kept in memory
 * only, and {@link #toString()} shows none of it.
 *
 * @param name      the name as the source has it
 * @param legalId   the personal identity number, null for a person without a Swedish one or with one that cannot be
 *                  used
 * @param positions the positions of the person, e.g. "Ledamot"
 */
public record Person(String name, LegalId legalId, List<String> positions) {

	public Person {
		positions = positions == null ? List.of() : List.copyOf(positions);
	}

	@Override
	public String toString() {
		return "Person[legalId=" + legalId + "]";
	}
}
