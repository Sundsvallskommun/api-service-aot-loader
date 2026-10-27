package se.sundsvall.aotloader.service;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.relation.Relation;
import generated.se.sundsvall.relation.ResourceIdentifier;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.integration.db.MigrationItemRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationItemEntity;
import se.sundsvall.aotloader.integration.partyassets.PartyAssetsIntegration;
import se.sundsvall.aotloader.integration.relation.RelationIntegration;
import se.sundsvall.aotloader.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.ItemStatus;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.support.ErrorTexts;
import se.sundsvall.aotloader.support.MigrationException;

import static se.sundsvall.aotloader.Constants.TAG_MIGRATION_ID;

/**
 * Removes what the migration wrote to the target systems for one item of the status table, and then the item, so that
 * the next run migrates it anew (section 10.3). In this order: the relations from its errand that the migration or
 * PartyAssets wrote and the relation the migration wrote to its errand, its post in PartyAssets and its errand. A post
 * is removed only when its asset id is the errand's and an errand only when it carries the item's tag migreringsid, so
 * that nothing else is removed. An errand the item does not name, as when a run stopped before it kept its id, is found
 * by that tag. The item has the status RENSAS from the first removal until it is gone, and what is removed before a
 * step fails is cleared from it, so the next cleanup goes on from there. A dry run only reads.
 */
@Component
class ItemCleaner {

	static final String CLEANUP_UNFINISHED = "Rensningen blev inte klar. Det som finns kvar i målsystemen tas bort av nästa rensning, och ärendet migreras inte förrän dess";

	private final SupportManagementIntegration supportManagement;
	private final PartyAssetsIntegration partyAssets;
	private final RelationIntegration relations;
	private final MigrationItemRepository itemRepository;
	private final AotLoaderProperties properties;
	private final Clock clock;

	ItemCleaner(final SupportManagementIntegration supportManagement, final PartyAssetsIntegration partyAssets, final RelationIntegration relations, final MigrationItemRepository itemRepository,
		final AotLoaderProperties properties, final Clock clock) {
		this.supportManagement = supportManagement;
		this.partyAssets = partyAssets;
		this.relations = relations;
		this.itemRepository = itemRepository;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * @param  item           the item of the status table
	 * @param  municipalityId the municipality the cleanup is started for
	 * @param  dryRun         true to only read and tell what would be removed
	 * @param  runId          the cleanup, which the item names as its last run once it has the status RENSAS
	 * @return                the row of the report: RENSAD, SKULLE_RENSAS or FEL
	 */
	ItemResult clean(final MigrationItemEntity item, final String municipalityId, final boolean dryRun, final String runId) {
		final var notes = new ReportNotes();
		try {
			final var key = new ItemKey(item.getSource(), item.getKind(), item.getSourceId());
			final var errand = findErrand(item, municipalityId, key);
			errand.ifPresent(found -> requireMigrated(found, key));
			final var errandId = errand.map(Errand::getId).orElse(item.getErrandId());
			final var asset = findAsset(item, municipalityId, errand);
			asset.ifPresent(found -> requireMigrated(found, Optional.ofNullable(errandId).orElse(item.getAssetId())));
			final var relationIds = relationsToRemove(item, errandId, municipalityId, asset);
			if (!dryRun) {
				remove(item, municipalityId, runId, relationIds, asset, errand);
			}
			notes.line(describe(dryRun, relationIds.size(), asset.isPresent(), errand.isPresent()));
			return notes.result(dryRun ? Outcome.SKULLE_RENSAS : Outcome.RENSAD, item.getErrandNumber());
		} catch (final RuntimeException e) {
			notes.line("Rensningen stannade: " + ErrorTexts.describe(e));
			return notes.result(Outcome.FEL, item.getErrandNumber());
		}
	}

	/**
	 * Gives the item the status RENSAS, with the cleanup as its last run and the time, and then removes the relations, the
	 * post and the errand, each step saved on the item before the next, and last the item. Until it is gone no run migrates
	 * it, since what the item says is then no longer in the target systems.
	 */
	private void remove(final MigrationItemEntity item, final String municipalityId, final String runId, final Set<String> relationIds, final Optional<Asset> asset, final Optional<Errand> errand) {
		item.setStatus(ItemStatus.RENSAS);
		item.setFailedStep(null);
		item.setReason(CLEANUP_UNFINISHED);
		item.setLastRunId(runId);
		item.setModified(OffsetDateTime.now(clock));
		itemRepository.save(item);
		relationIds.forEach(id -> {
			relations.deleteRelation(municipalityId, id);
			// The relation may be kept on the inspection errand it leads to, and is gone from there as well
			itemRepository.findByRelationId(id).filter(owner -> !Objects.equals(owner.getId(), item.getId())).ifPresent(owner -> {
				owner.setRelationId(null);
				itemRepository.save(owner);
			});
		});
		item.setRelationId(null);
		itemRepository.save(item);
		asset.ifPresent(found -> partyAssets.deleteAsset(municipalityId, found.getId()));
		item.setPartyAssetsId(null);
		item.setAssetId(null);
		itemRepository.save(item);
		errand.ifPresent(found -> supportManagement.deleteErrand(municipalityId, found.getId()));
		itemRepository.delete(item);
	}

	/**
	 * @return the post of the item: the one the item names, or else, for a permit or notification, the one a run that
	 *         stopped half way created on the holder of the errand, under the errand's id as asset id (decision 4)
	 */
	private Optional<Asset> findAsset(final MigrationItemEntity item, final String municipalityId, final Optional<Errand> errand) {
		if (item.getPartyAssetsId() != null) {
			return partyAssets.getAsset(municipalityId, item.getPartyAssetsId());
		}
		if (item.getErrandType() == null || !item.getErrandType().isWithAsset()) {
			return Optional.empty();
		}
		return errand.flatMap(found -> holderPartyId(found).flatMap(partyId -> partyAssets.findAsset(municipalityId, partyId, found.getId())));
	}

	private Optional<String> holderPartyId(final Errand errand) {
		return Optional.ofNullable(errand.getStakeholders()).orElse(List.of()).stream()
			.filter(stakeholder -> properties.roles().permitHolder().equals(stakeholder.getRole()))
			.map(Stakeholder::getExternalId)
			.filter(Objects::nonNull)
			.findFirst();
	}

	/**
	 * The relations the migration and PartyAssets wrote: the one the item keeps, from the permit errand to its errand, and
	 * those from its errand to its post and to the errands of other items. The inverses the relation service writes are
	 * never among them, since they go when their relation is removed.
	 *
	 * @return the ids of the relations, in the order to remove them
	 */
	private Set<String> relationsToRemove(final MigrationItemEntity item, final String errandId, final String municipalityId, final Optional<Asset> asset) {
		final var ids = new LinkedHashSet<String>();
		Optional.ofNullable(item.getRelationId()).ifPresent(ids::add);
		if (errandId == null) {
			return ids;
		}
		final var assetId = asset.map(Asset::getId).orElse(null);
		relations.findRelationsFrom(municipalityId, errandId).stream()
			.filter(relation -> isToAsset(relation, assetId) || itemRepository.findByRelationId(relation.getId()).isPresent())
			.map(Relation::getId)
			.forEach(ids::add);
		return ids;
	}

	private static boolean isToAsset(final Relation relation, final String assetId) {
		return assetId != null && assetId.equals(Optional.ofNullable(relation.getTarget()).map(ResourceIdentifier::getResourceId).orElse(null));
	}

	private static void requireMigrated(final Errand errand, final ItemKey key) {
		final var tagged = Optional.ofNullable(errand.getExternalTags()).orElse(Set.of()).stream()
			.anyMatch(tag -> TAG_MIGRATION_ID.equals(tag.getKey()) && key.migrationId().equals(tag.getValue()));
		if (!tagged) {
			throw new MigrationException("Ärendet %s har inte migreringens tagg %s=%s och tas inte bort".formatted(errand.getErrandNumber(), TAG_MIGRATION_ID, key.migrationId()));
		}
	}

	/**
	 * @return                    the errand of the item: the one the item names, or else, for an item that reached the
	 *                            target systems, the one with its tag migreringsid, as when a run stopped before it kept
	 *                            the id of the errand it created
	 * @throws MigrationException when more than one errand has the tag, and nothing is then removed
	 */
	private Optional<Errand> findErrand(final MigrationItemEntity item, final String municipalityId, final ItemKey key) {
		final var named = Optional.ofNullable(item.getErrandId()).flatMap(id -> supportManagement.getErrand(municipalityId, id));
		// An item without a municipality never reached the target systems
		if (named.isPresent() || item.getMunicipalityId() == null) {
			return named;
		}
		final var tagged = supportManagement.findErrandsByExternalTag(municipalityId, TAG_MIGRATION_ID, key.migrationId());
		if (tagged.size() > 1) {
			throw new MigrationException("SupportManagement har %d ärenden med migreringsid %s, och inget av dem tas bort".formatted(tagged.size(), key.migrationId()));
		}
		return tagged.stream().findFirst();
	}

	private static void requireMigrated(final Asset asset, final String expected) {
		if (expected == null || !expected.equals(asset.getAssetId())) {
			throw new MigrationException("Posten %s i PartyAssets har inte ärendets id som tillstånds-id och tas inte bort".formatted(asset.getId()));
		}
	}

	private static String describe(final boolean dryRun, final int relationCount, final boolean asset, final boolean errand) {
		final var parts = new ArrayList<String>();
		if (relationCount > 0) {
			parts.add(relationCount == 1 ? "1 relation" : relationCount + " relationer");
		}
		if (asset) {
			parts.add("tillståndet i PartyAssets");
		}
		if (errand) {
			parts.add("ärendet i SupportManagement");
		}
		if (parts.isEmpty()) {
			return dryRun ? "Inget finns i målsystemen; raden i statustabellen tas bort" : "Inget fanns i målsystemen; raden i statustabellen är borttagen";
		}
		return (dryRun ? "Tas bort: " : "Borttaget: ") + String.join(", ", parts) + " och raden i statustabellen";
	}
}
