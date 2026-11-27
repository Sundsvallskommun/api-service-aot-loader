package se.sundsvall.aotloader.service.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The partyIds found for the holder and the persons with significant influence of a candidate.
 *
 * @param holder  the partyId of the holder, null when it has none
 * @param persons the partyIds of the persons, in the order of the candidate's persons, null for one without
 */
public record PartyIds(String holder, List<String> persons) {

	public PartyIds {
		// Not List.copyOf, which refuses the nulls of the persons without partyId
		persons = persons == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(persons));
	}

	public Optional<String> holderPartyId() {
		return Optional.ofNullable(holder);
	}

	/**
	 * @return the partyId of the person at the index of the candidate's persons, empty when it has none
	 */
	public Optional<String> personPartyId(final int index) {
		return index < persons.size() ? Optional.ofNullable(persons.get(index)) : Optional.empty();
	}
}
