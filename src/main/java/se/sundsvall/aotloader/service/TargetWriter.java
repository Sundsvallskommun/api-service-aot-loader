package se.sundsvall.aotloader.service;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.partyassets.Status;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Errand.LifecycleEnum;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
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
			final var createdId = supportManagement.createErrand(municipalityId, mapper.toNewDraft(candidate, municipalityId, parties, labelIds));
			errand = supportManagement.getErrand(municipalityId, createdId)
				.orElseThrow(() -> new MigrationException("Ärendet %s som just skapades gick inte att läsa".formatted(createdId)));
		} else if (found.get().getLifecycle() == LifecycleEnum.DRAFT) {
			errand = supportManagement.updateErrand(municipalityId, found.get().getId(), mapper.toDraftUpdate(candidate, municipalityId, parties, labelIds));
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
	 * Finds the post in PartyAssets, by its id in the status table or by the errand id and the partyId, and creates it
	 * only when neither finds it. A 409 on the create means it exists after all; it is then read, since the answer has no
	 * id. A post found, made by an earlier run, is brought up to date with what the item gives now.
	 *
	 * @param  candidate          the item
	 * @param  municipalityId     the municipality the item belongs to
	 * @param  errandId           the id of the errand, which is the asset id of the post (decision 4)
	 * @param  partyAssetsId      the id of the post in the status table, null when it has none
	 * @param  parties            the partyIds; the holder's is required unless the post is found by its id
	 * @param  expired            true when the post is to be EXPIRED, false when ACTIVE
	 * @param  notes              the report of the item, told what was found and changed
	 * @return                    the id PartyAssets gave the post
	 * @throws MigrationException when the post found has a status the migration never gives, or belongs to another party
	 *                            than the item now gives; it is then left as it is
	 */
	String ensureAsset(final Candidate candidate, final String municipalityId, final String errandId, final String partyAssetsId, final PartyIds parties, final boolean expired,
		final ReportNotes notes) {
		final var found = Optional.ofNullable(partyAssetsId).flatMap(id -> assetInStatusTable(municipalityId, id, notes))
			.or(() -> partyAssets.findAsset(municipalityId, holderPartyId(parties), errandId));
		if (found.isPresent()) {
			// The holder may be gone from Party since the post was made; the post keeps its party anyway
			final var wanted = mapper.toAsset(candidate, municipalityId, errandId, parties.holderPartyId().orElse(found.get().getPartyId()), expired);
			return bringUpToDate(municipalityId, found.get(), wanted, notes);
		}
		final var wanted = mapper.toAsset(candidate, municipalityId, errandId, holderPartyId(parties), expired);
		return partyAssets.createAsset(municipalityId, mapper.toSourceReference(errandId), wanted)
			.orElseGet(() -> bringUpToDate(municipalityId, partyAssets.findAsset(municipalityId, wanted.getPartyId(), errandId)
				.orElseThrow(() -> new MigrationException("PartyAssets svarade att tillståndet redan finns, men det gick inte att hitta")), wanted, notes));
	}

	/**
	 * Gives an EXPIRED post the status ACTIVE while it gets its files, since an EXPIRED post takes none, and EXPIRED back
	 * once it has them.
	 *
	 * @throws MigrationException when the post does not get the status, or is gone
	 */
	void changeAssetStatus(final String municipalityId, final String partyAssetsId, final Status status, final ReportNotes notes) {
		partyAssets.updateAsset(municipalityId, partyAssetsId, new AssetUpdateRequest().status(status));
		final var updated = partyAssets.getAsset(municipalityId, partyAssetsId)
			.orElseThrow(() -> new MigrationException("Tillståndet %s fanns inte i PartyAssets efter ändringen".formatted(partyAssetsId)));
		if (updated.getStatus() != status) {
			throw new MigrationException("Tillståndet %s i PartyAssets fick inte status %s".formatted(partyAssetsId, status));
		}
		notes.line(status == Status.ACTIVE
			? "Tillståndet i PartyAssets hade status ACTIVE medan det fick sina filer, eftersom ett EXPIRED tillstånd inte tar emot filer"
			: "Tillståndet i PartyAssets fick status EXPIRED igen när det hade fått sina filer");
	}

	private static String holderPartyId(final PartyIds parties) {
		return parties.holderPartyId().orElseThrow(() -> new MigrationException("Tillståndshavaren saknar partyId"));
	}

	private Optional<Asset> assetInStatusTable(final String municipalityId, final String partyAssetsId, final ReportNotes notes) {
		final var asset = partyAssets.getAsset(municipalityId, partyAssetsId);
		if (asset.isEmpty()) {
			notes.line("Tillståndet %s fanns inte längre i PartyAssets".formatted(partyAssetsId));
		}
		return asset;
	}

	/**
	 * Gives a post an earlier run made the status the item is to have, and the title, last day and parameters it gives
	 * now. PartyAssets changes those only while the post is ACTIVE, so a post that stays EXPIRED keeps them, and the report
	 * says so. A post that is to become EXPIRED gets its content first, checked, and its status after.
	 *
	 * @return                    the id of the post
	 * @throws MigrationException when the post has a status the migration never gives, or belongs to another party than
	 *                            the item now gives, which is then left as it is; and when PartyAssets does not make the
	 *                            change
	 */
	private String bringUpToDate(final String municipalityId, final Asset asset, final AssetCreateRequest wanted, final ReportNotes notes) {
		final var from = asset.getStatus();
		final var to = wanted.getStatus();
		if (from != Status.ACTIVE && from != Status.EXPIRED) {
			throw new MigrationException("Tillståndet %s i PartyAssets har status %s, som migreringen aldrig ger, och ändras inte".formatted(asset.getId(), from));
		}
		// The content describes the holder, so another party's post must not get it
		if (!Objects.equals(asset.getPartyId(), wanted.getPartyId())) {
			throw new MigrationException("Tillståndet %s i PartyAssets tillhör en annan part än den källan anger nu och ändras inte (avsnitt 10.3)".formatted(asset.getId()));
		}
		final var content = mapper.toContentUpdate(asset, wanted);
		final var statusChanges = from != to;
		final var contentChanges = content.isPresent() && (from == Status.ACTIVE || statusChanges);
		if (from == Status.ACTIVE) {
			// Checked before the status changes, since an EXPIRED post can no longer get the content
			if (content.isPresent()) {
				partyAssets.updateAsset(municipalityId, asset.getId(), content.get());
				verifyUpdated(municipalityId, asset.getId(), wanted, from);
			}
			if (statusChanges) {
				partyAssets.updateAsset(municipalityId, asset.getId(), new AssetUpdateRequest().status(to));
				verifyUpdated(municipalityId, asset.getId(), wanted, to);
			}
		} else if (statusChanges) {
			partyAssets.updateAsset(municipalityId, asset.getId(), new AssetUpdateRequest().status(to));
			content.ifPresent(update -> partyAssets.updateAsset(municipalityId, asset.getId(), update));
			verifyUpdated(municipalityId, asset.getId(), wanted, to);
		}

		if (statusChanges) {
			notes.line("Tillståndet i PartyAssets hade status %s från en tidigare körning och fick status %s".formatted(from, to));
		}
		if (contentChanges) {
			notes.line("Tillståndet i PartyAssets från en tidigare körning fick titel, giltighet och parametrar som källan ger nu");
		} else if (content.isPresent()) {
			notes.line("Tillståndet i PartyAssets är EXPIRED, och dess titel, giltighet och parametrar kan därför inte få det källan ger nu");
		}
		unchangeable(asset, wanted).ifPresent(notes::line);
		return asset.getId();
	}

	/**
	 * Reads the post again and checks that it has the status it was given and the content the item gives.
	 *
	 * @throws MigrationException when it does not, or when it is gone
	 */
	private void verifyUpdated(final String municipalityId, final String id, final AssetCreateRequest wanted, final Status status) {
		final var updated = partyAssets.getAsset(municipalityId, id)
			.orElseThrow(() -> new MigrationException("Tillståndet %s fanns inte i PartyAssets efter ändringen".formatted(id)));
		// PartyAssets before 6.8 answers a change of the content with 204 and leaves the content as it was
		if (updated.getStatus() != status || mapper.toContentUpdate(updated, wanted).isPresent()) {
			throw new MigrationException("PartyAssets gjorde inte hela ändringen av tillståndet %s, vilket kräver PartyAssets 6.8 eller senare".formatted(id));
		}
	}

	/**
	 * @return a note on what differs from what the item gives now but cannot be changed in PartyAssets, empty when nothing
	 *         does
	 */
	private static Optional<String> unchangeable(final Asset asset, final AssetCreateRequest wanted) {
		final var differs = new ArrayList<String>();
		if (!Objects.equals(asset.getType(), wanted.getType())) {
			differs.add("typ");
		}
		if (!Objects.equals(asset.getIssued(), wanted.getIssued())) {
			differs.add("utfärdandedatum");
		}
		if (!Objects.equals(asset.getDescription(), wanted.getDescription())) {
			differs.add("beskrivning");
		}
		return differs.isEmpty() ? Optional.empty()
			: Optional.of("Tillståndets %s i PartyAssets skiljer sig från det källan ger nu och kan inte ändras där (avsnitt 10.3)".formatted(String.join(", ", differs)));
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
