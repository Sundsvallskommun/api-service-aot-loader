package se.sundsvall.aotloader.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.integration.party.PartyIntegration;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.PartyIds;
import se.sundsvall.aotloader.service.model.RuleHit;

import static se.sundsvall.aotloader.service.model.ReviewRule.HOLDER_NOT_IN_PARTY;
import static se.sundsvall.aotloader.service.model.ReviewRule.INSPECTION_HOLDER_NOT_IN_PARTY;

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
	 * person.
	 *
	 * @param  candidate      the item
	 * @param  municipalityId the municipality the item belongs to
	 * @param  context        the run, which remembers the partyIds found
	 * @param  hits           the rule hits of the item, which the hits on Party are added to
	 * @param  notes          the report row of the item
	 * @return                the partyIds found
	 */
	PartyIds resolve(final Candidate candidate, final String municipalityId, final RunContext context, final List<RuleHit> hits, final ReportNotes notes) {
		final var holder = candidate.holder();
		final var holderPartyId = Optional.ofNullable(holder).flatMap(found -> lookup(found.legalId(), municipalityId, context));
		if (holder != null && holderPartyId.isEmpty()) {
			if (candidate.type().getKind().isInspection()) {
				hits.add(RuleHit.of(INSPECTION_HOLDER_NOT_IN_PARTY, holder.legalId() == null ? "numret saknas eller har fel format" : null));
			} else if (holder.legalId() != null) {
				hits.add(RuleHit.of(HOLDER_NOT_IN_PARTY));
			}
		}
		final var personPartyIds = new ArrayList<String>();
		var notFound = 0;
		for (final var person : candidate.persons()) {
			final var partyId = lookup(person.legalId(), municipalityId, context);
			personPartyIds.add(partyId.orElse(null));
			if (person.legalId() != null && partyId.isEmpty()) {
				notFound++;
			}
		}
		if (notFound > 0) {
			notes.line("%d person(er) med betydande inflytande finns inte i Party och läggs in utan partyId".formatted(notFound));
		}
		return new PartyIds(holderPartyId.orElse(null), personPartyIds);
	}

	private Optional<String> lookup(final LegalId legalId, final String municipalityId, final RunContext context) {
		return legalId == null ? Optional.empty() : context.partyId(municipalityId, legalId, id -> party.getPartyId(municipalityId, id));
	}
}
