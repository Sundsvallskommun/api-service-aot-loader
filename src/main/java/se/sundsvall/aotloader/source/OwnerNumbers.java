package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import se.sundsvall.aotloader.service.model.RuleHit;

import static se.sundsvall.aotloader.service.model.ReviewRule.LEGAL_ID_ON_SEVERAL_OWNERS;

/**
 * The owner posts of a source by their number, however it is written, for G7.
 */
public final class OwnerNumbers {

	private final Map<String, Set<Integer>> ownersByNumber = new HashMap<>();
	private final LocalDate today;

	/**
	 * @param today the date the century of a personal identity number is chosen from
	 */
	public OwnerNumbers(final LocalDate today) {
		this.today = today;
	}

	/**
	 * Counts the owner post, unless it is null or has no number.
	 */
	public void add(final ContactRow owner) {
		numberOf(owner).ifPresent(number -> ownersByNumber.computeIfAbsent(number, _ -> new HashSet<>()).add(owner.id()));
	}

	/**
	 * G7 when another owner post has the number of the owner.
	 *
	 * @param owner the owner, null when there is none
	 */
	public void hits(final ContactRow owner, final List<RuleHit> hits) {
		numberOf(owner)
			.filter(number -> ownersByNumber.getOrDefault(number, Set.of()).size() > 1)
			.ifPresent(_ -> hits.add(RuleHit.of(LEGAL_ID_ON_SEVERAL_OWNERS, "AgarID " + owner.id())));
	}

	private Optional<String> numberOf(final ContactRow owner) {
		return Optional.ofNullable(owner).flatMap(found -> PartyRows.numberKey(found.organisationsNr(), today));
	}
}
