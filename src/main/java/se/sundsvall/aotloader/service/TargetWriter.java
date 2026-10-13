package se.sundsvall.aotloader.service;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Errand.LifecycleEnum;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.integration.partyassets.PartyAssetsIntegration;
import se.sundsvall.aotloader.integration.relation.RelationIntegration;
import se.sundsvall.aotloader.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.aotloader.service.mapper.TargetMapper;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.PartyIds;
import se.sundsvall.aotloader.support.MigrationException;

import static se.sundsvall.aotloader.Constants.RELATION_TYPE_LINK;
import static se.sundsvall.aotloader.Constants.TAG_MIGRATION_ID;

/**
 * Writes an item to the target systems. Each method looks for what is already there before it creates anything, so
 * that a step can be made again without creating a second errand, permit or relation (K3).
 */
@Component
class TargetWriter {

	private final SupportManagementIntegration supportManagement;
	private final PartyAssetsIntegration partyAssets;
	private final RelationIntegration relations;
	private final TargetMapper mapper;

	TargetWriter(final SupportManagementIntegration supportManagement, final PartyAssetsIntegration partyAssets, final RelationIntegration relations, final TargetMapper mapper) {
		this.supportManagement = supportManagement;
		this.partyAssets = partyAssets;
		this.relations = relations;
		this.mapper = mapper;
	}

	/**
	 * Finds the errand of the item, by its id in the status table or by its external tag with drafts included, and creates
	 * it as a draft only when neither finds it. A draft found is updated with what the source says now; an active errand
	 * is left as it is.
	 *
	 * @param  candidate      the item
	 * @param  municipalityId the municipality the item belongs to
	 * @param  errandId       the id of the errand in the status table, null when it has none
	 * @param  parties        the partyIds of the holder and the persons
	 * @param  labelIds       the ids of the labels of the municipality
	 * @return                the errand, with its life cycle
	 */
	Errand ensureErrand(final Candidate candidate, final String municipalityId, final String errandId, final PartyIds parties, final Map<String, String> labelIds) {
		final var found = Optional.ofNullable(errandId)
			.flatMap(id -> supportManagement.getErrand(municipalityId, id))
			.or(() -> findByMigrationId(municipalityId, candidate.key()));
		final Errand errand;
		if (found.isEmpty()) {
			final var createdId = supportManagement.createErrand(municipalityId, mapper.toNewDraft(candidate, parties, labelIds));
			errand = supportManagement.getErrand(municipalityId, createdId)
				.orElseThrow(() -> new MigrationException("Ärendet %s som just skapades gick inte att läsa".formatted(createdId)));
		} else if (found.get().getLifecycle() == LifecycleEnum.DRAFT) {
			errand = supportManagement.updateErrand(municipalityId, found.get().getId(), mapper.toDraftUpdate(candidate, parties, labelIds));
		} else {
			errand = found.get();
		}
		if (errand.getLifecycle() == null) {
			throw new MigrationException("SupportManagement svarade utan livscykel på ärendet, och migreringen kräver utkast");
		}
		return errand;
	}

	/**
	 * Makes the draft an active errand, the last step of the flow.
	 */
	void activate(final String municipalityId, final String errandId) {
		supportManagement.updateErrand(municipalityId, errandId, mapper.toActivation());
	}

	/**
	 * Finds the post in PartyAssets by the errand id and the partyId, and creates it only when it is not found. A 409 on
	 * the create means it exists after all; it is then read, since the answer has no id.
	 *
	 * @param  candidate      the item
	 * @param  municipalityId the municipality the item belongs to
	 * @param  errandId       the id of the errand, which is the asset id of the post (decision 4)
	 * @param  parties        the partyIds, of which the holder's is required
	 * @param  expired        true to create the post as EXPIRED
	 * @return                the id PartyAssets gave the post
	 */
	String ensureAsset(final Candidate candidate, final String municipalityId, final String errandId, final PartyIds parties, final boolean expired) {
		final var partyId = parties.holderPartyId().orElseThrow(() -> new MigrationException("Tillståndshavaren saknar partyId"));
		return partyAssets.findAsset(municipalityId, partyId, errandId).map(Asset::getId)
			.or(() -> partyAssets.createAsset(municipalityId, mapper.toSourceReference(errandId), mapper.toAsset(candidate, errandId, partyId, expired)))
			.or(() -> partyAssets.findAsset(municipalityId, partyId, errandId).map(Asset::getId))
			.orElseThrow(() -> new MigrationException("PartyAssets svarade att tillståndet redan finns, men det gick inte att hitta"));
	}

	/**
	 * Finds the relation from the permit errand to the inspection errand, and creates it only when it is not found. When
	 * more than one is found, none is created and the deviation is reported.
	 *
	 * @return the id of the relation, empty when several were found
	 */
	Optional<String> ensureRelation(final String municipalityId, final String permitErrandId, final String errandId, final ReportNotes notes) {
		final var found = relations.findRelations(municipalityId, RELATION_TYPE_LINK, permitErrandId, errandId);
		if (found.size() > 1) {
			notes.line("Avvikelse: %d relationer finns redan från tillståndsärendet till ärendet, ingen ny skapas".formatted(found.size()));
			return Optional.empty();
		}
		return Optional.ofNullable(found.isEmpty()
			? relations.createRelation(municipalityId, mapper.toRelation(permitErrandId, errandId))
			: found.getFirst().getId());
	}

	private Optional<Errand> findByMigrationId(final String municipalityId, final ItemKey key) {
		final var found = supportManagement.findErrandsByExternalTag(municipalityId, TAG_MIGRATION_ID, key.migrationId());
		if (found.size() > 1) {
			throw new MigrationException("SupportManagement har %d ärenden med migreringsid %s, inget nytt skapas".formatted(found.size(), key.migrationId()));
		}
		return found.stream().findFirst();
	}
}
