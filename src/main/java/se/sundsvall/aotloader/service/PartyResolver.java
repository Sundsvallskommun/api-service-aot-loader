package se.sundsvall.aotloader.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.integration.party.PartyIntegration;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.PartyIds;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.support.MigrationException;
import se.sundsvall.aotloader.support.SystemUnavailableException;

import static se.sundsvall.aotloader.service.model.ReviewRule.HOLDER_NOT_IN_PARTY;
import static se.sundsvall.aotloader.service.model.ReviewRule.INSPECTION_HOLDER_NOT_IN_PARTY;
import static se.sundsvall.aotloader.service.model.ReviewRule.PERSON_LOOKUP_FAILED;

/**
 * Looks up the partyIds of the holder and the persons of an item in Party.
 */
@Component
class PartyResolver {

	private final PartyIntegration party;

	PartyResolver(final PartyIntegration party) {
		this.party = party;
	}

	/**
	 * Looks up the partyIds. A permit holder that Party does not know hits G10 and stops the item; the holder of an
	 * inspection errand, often a former holder, hits G10 as information only and is added without partyId, and so is a
	 * person. The report row counts the persons Party does not know and those whose number is missing or cannot be used,
	 * unless the source marks them as foreign. A person whose lookup Party answers with an error is added without partyId,
	 * and G30 tells of it (decision 121). A lookup that could not reach Party fails the item, so that a later run looks
	 * the person up again rather than a passing failure adding the person without partyId.
	 *
	 * @param  candidate          the item
	 * @param  municipalityId     the municipality the item belongs to
	 * @param  context            the run, which remembers the partyIds found
	 * @param  hits               the rule hits of the item, which the hits on Party are added to
	 * @param  notes              the report row of the item
	 * @return                    the partyIds found
	 * @throws MigrationException when the lookup of the holder fails, or a lookup could not reach Party
	 */
	PartyIds resolve(final Candidate candidate, final String municipalityId, final RunContext context, final List<RuleHit> hits, final ReportNotes notes) {
		final var holder = candidate.holder();
		final var holderPartyId = Optional.ofNullable(holder).flatMap(found -> lookup(found.legalId(), municipalityId, context));
		if (holder != null && holderPartyId.isEmpty()) {
			holderNotFound(candidate, holder, hits);
		}
		return new PartyIds(holderPartyId.orElse(null), personPartyIds(candidate, municipalityId, context, hits, notes));
	}

	/**
	 * G10 for a holder that Party does not know: a hit that stops a permit, and information only for the holder of an
	 * inspection or documentation errand, also one without a number. A permit holder without a number has hit G8 already.
	 */
	private static void holderNotFound(final Candidate candidate, final Holder holder, final List<RuleHit> hits) {
		if (candidate.type().getKind().hasConcernedHolder()) {
			hits.add(RuleHit.of(INSPECTION_HOLDER_NOT_IN_PARTY, holder.legalId() == null ? "numret saknas eller har fel format" : null));
		} else if (holder.legalId() != null) {
			hits.add(RuleHit.of(HOLDER_NOT_IN_PARTY));
		}
	}

	/**
	 * @return the partyIds of the persons, in their order, null for a person Party does not know or whose lookup failed
	 */
	private List<String> personPartyIds(final Candidate candidate, final String municipalityId, final RunContext context, final List<RuleHit> hits, final ReportNotes notes) {
		final var personPartyIds = new ArrayList<String>();
		var notFound = 0;
		var withoutNumber = 0;
		final var failures = new ArrayList<String>();
		for (final var person : candidate.persons()) {
			final Optional<String> partyId;
			try {
				partyId = lookup(person.legalId(), municipalityId, context);
			} catch (final SystemUnavailableException e) {
				throw e;
			} catch (final MigrationException e) {
				// Unlike the holder's, a person's partyId is not needed to migrate the item, so the person is added without it
				failures.add(e.getMessage());
				personPartyIds.add(null);
				continue;
			}
			personPartyIds.add(partyId.orElse(null));
			if (person.legalId() == null && !person.foreign()) {
				withoutNumber++;
			} else if (person.legalId() != null && partyId.isEmpty()) {
				notFound++;
			}
		}
		if (!failures.isEmpty()) {
			hits.add(RuleHit.of(PERSON_LOOKUP_FAILED, "%d person(er), %s".formatted(failures.size(), failures.getFirst())));
		}
		if (notFound > 0) {
			notes.line("%d person(er) med betydande inflytande finns inte i Party och läggs in utan partyId".formatted(notFound));
		}
		if (withoutNumber > 0) {
			notes.line("%d person(er) med betydande inflytande saknar nummer eller har ett nummer med fel format och läggs in utan partyId".formatted(withoutNumber));
		}
		return personPartyIds;
	}

	private Optional<String> lookup(final LegalId legalId, final String municipalityId, final RunContext context) {
		return legalId == null ? Optional.empty() : context.partyId(municipalityId, legalId, id -> party.getPartyId(municipalityId, id));
	}
}
