package se.sundsvall.aotloader.service;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.relation.Relation;
import generated.se.sundsvall.relation.ResourceIdentifier;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ExternalTag;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.aotloader.integration.db.MigrationItemRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationItemEntity;
import se.sundsvall.aotloader.integration.partyassets.PartyAssetsIntegration;
import se.sundsvall.aotloader.integration.relation.RelationIntegration;
import se.sundsvall.aotloader.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.dept44.problem.Problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CONFLICT;

@ExtendWith(MockitoExtension.class)
class ItemCleanerTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String ERRAND_ID = "errand-1";
	private static final String ASSET_ID = "asset-1";

	@Mock
	private SupportManagementIntegration supportManagement;

	@Mock
	private PartyAssetsIntegration partyAssets;

	@Mock
	private RelationIntegration relations;

	@Mock
	private MigrationItemRepository itemRepository;

	private ItemCleaner cleaner;

	@BeforeEach
	void setUp() {
		cleaner = new ItemCleaner(supportManagement, partyAssets, relations, itemRepository, TestData.properties());
		lenient().when(itemRepository.findByRelationId(anyString())).thenReturn(Optional.empty());
	}

	@Test
	void aPermitGoesWithTheRelationsTheMigrationAndPartyAssetsWroteItsPostAndItsErrand() {
		final var permit = permit();
		final var inspection = item(Kind.TILLSYN, "700");
		inspection.setRelationId("to-inspection");
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(migratedErrand(ERRAND_ID, "ALKT-TILLSTAND-1")));
		when(partyAssets.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(new Asset().id(ASSET_ID).assetId(ERRAND_ID)));
		when(relations.findRelationsFrom(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of(
			relation("to-asset", ERRAND_ID, ASSET_ID),
			relation("to-inspection", ERRAND_ID, "errand-700"),
			relation("inverse", ERRAND_ID, "errand-701"),
			relation("by-a-case-handler", ERRAND_ID, "other-errand")));
		when(itemRepository.findByRelationId("to-inspection")).thenReturn(Optional.of(inspection));

		final var result = cleaner.clean(permit, MUNICIPALITY_ID, false);

		assertThat(result.outcome()).isEqualTo(Outcome.RENSAD);
		assertThat(result.detail()).isEqualTo("Borttaget: 2 relationer, tillståndet i PartyAssets, ärendet i SupportManagement och raden i statustabellen");
		assertThat(result.errandNumber()).isEqualTo("ALKT-2026-1");
		// The relations go first and the errand last, so that nothing is left that points at what is gone
		final InOrder order = inOrder(relations, partyAssets, supportManagement, itemRepository);
		order.verify(relations).deleteRelation(MUNICIPALITY_ID, "to-asset");
		order.verify(relations).deleteRelation(MUNICIPALITY_ID, "to-inspection");
		order.verify(partyAssets).deleteAsset(MUNICIPALITY_ID, ASSET_ID);
		order.verify(supportManagement).deleteErrand(MUNICIPALITY_ID, ERRAND_ID);
		order.verify(itemRepository).delete(permit);
		verify(relations, never()).deleteRelation(MUNICIPALITY_ID, "inverse");
		verify(relations, never()).deleteRelation(MUNICIPALITY_ID, "by-a-case-handler");
		// The inspection errand no longer has the relation it kept
		assertThat(inspection.getRelationId()).isNull();
		verify(itemRepository).save(inspection);
	}

	@Test
	void aDryRunOnlyReads() {
		final var permit = permit();
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(migratedErrand(ERRAND_ID, "ALKT-TILLSTAND-1")));
		when(partyAssets.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(new Asset().id(ASSET_ID).assetId(ERRAND_ID)));
		when(relations.findRelationsFrom(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of(relation("to-asset", ERRAND_ID, ASSET_ID)));

		final var result = cleaner.clean(permit, MUNICIPALITY_ID, true);

		assertThat(result.outcome()).isEqualTo(Outcome.SKULLE_RENSAS);
		assertThat(result.detail()).isEqualTo("Tas bort: 1 relation, tillståndet i PartyAssets, ärendet i SupportManagement och raden i statustabellen");
		verify(relations, never()).deleteRelation(anyString(), anyString());
		verify(partyAssets, never()).deleteAsset(anyString(), anyString());
		verify(supportManagement, never()).deleteErrand(anyString(), anyString());
		verify(itemRepository, never()).delete(any());
		verify(itemRepository, never()).save(any());
	}

	@Test
	void anErrandWithoutTheTagOfTheItemIsNotRemoved() {
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(migratedErrand(ERRAND_ID, "ALKT-TILLSTAND-2")));

		final var result = cleaner.clean(permit(), MUNICIPALITY_ID, false);

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).startsWith("Rensningen stannade: Ärendet ALKT-2026-1 har inte migreringens tagg migreringsid=ALKT-TILLSTAND-1");
		verify(supportManagement, never()).deleteErrand(anyString(), anyString());
		verify(partyAssets, never()).deleteAsset(anyString(), anyString());
		verify(itemRepository, never()).delete(any());
	}

	@Test
	void aPostOfAnotherErrandIsNotRemoved() {
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(migratedErrand(ERRAND_ID, "ALKT-TILLSTAND-1")));
		when(partyAssets.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(new Asset().id(ASSET_ID).assetId("decision-of-pw-alkt")));

		final var result = cleaner.clean(permit(), MUNICIPALITY_ID, false);

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).contains("Posten asset-1 i PartyAssets har inte ärendets id som tillstånds-id");
		verify(partyAssets, never()).deleteAsset(anyString(), anyString());
		verify(supportManagement, never()).deleteErrand(anyString(), anyString());
	}

	@Test
	void thePostOfARunThatStoppedHalfWayIsFoundOnTheHolder() {
		final var permit = permit();
		permit.setPartyAssetsId(null);
		permit.setAssetId(null);
		final var errand = migratedErrand(ERRAND_ID, "ALKT-TILLSTAND-1").stakeholders(List.of(
			new Stakeholder().role("CONTACT").externalId("person-party-id"),
			new Stakeholder().role("PRIMARY").externalId("holder-party-id")));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand));
		when(partyAssets.findAsset(MUNICIPALITY_ID, "holder-party-id", ERRAND_ID)).thenReturn(Optional.of(new Asset().id(ASSET_ID).assetId(ERRAND_ID)));
		when(relations.findRelationsFrom(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of(relation("to-asset", ERRAND_ID, ASSET_ID)));

		final var result = cleaner.clean(permit, MUNICIPALITY_ID, false);

		assertThat(result.outcome()).isEqualTo(Outcome.RENSAD);
		verify(relations).deleteRelation(MUNICIPALITY_ID, "to-asset");
		verify(partyAssets).deleteAsset(MUNICIPALITY_ID, ASSET_ID);
	}

	@Test
	void anInspectionRemovesTheRelationItKeepsButNotItsInverse() {
		final var inspection = item(Kind.TILLSYN, "700");
		inspection.setErrandType(ErrandType.ALCOHOL_INSPECTION);
		inspection.setErrandId("errand-700");
		inspection.setRelationId("to-inspection");
		when(supportManagement.getErrand(MUNICIPALITY_ID, "errand-700")).thenReturn(Optional.of(migratedErrand("errand-700", "ALKT-TILLSYN-700")));
		when(relations.findRelationsFrom(MUNICIPALITY_ID, "errand-700")).thenReturn(List.of(relation("inverse-of-to-inspection", "errand-700", ERRAND_ID)));

		final var result = cleaner.clean(inspection, MUNICIPALITY_ID, false);

		assertThat(result.outcome()).isEqualTo(Outcome.RENSAD);
		assertThat(result.detail()).isEqualTo("Borttaget: 1 relation, ärendet i SupportManagement och raden i statustabellen");
		verify(relations).deleteRelation(MUNICIPALITY_ID, "to-inspection");
		verify(relations, never()).deleteRelation(MUNICIPALITY_ID, "inverse-of-to-inspection");
		verify(partyAssets, never()).getAsset(anyString(), anyString());
		verify(supportManagement).deleteErrand(MUNICIPALITY_ID, "errand-700");
	}

	@Test
	void anItemWithNothingLeftInTheTargetSystemsIsRemoved() {
		final var item = item(Kind.TILLSTAND, "3");
		when(relations.findRelationsFrom(MUNICIPALITY_ID, "gone")).thenReturn(List.of());
		item.setErrandId("gone");
		when(supportManagement.getErrand(MUNICIPALITY_ID, "gone")).thenReturn(Optional.empty());

		final var result = cleaner.clean(item, MUNICIPALITY_ID, false);

		assertThat(result.outcome()).isEqualTo(Outcome.RENSAD);
		assertThat(result.detail()).isEqualTo("Inget fanns i målsystemen; raden i statustabellen är borttagen");
		verify(supportManagement, never()).deleteErrand(anyString(), anyString());
		verify(itemRepository).delete(item);
		// An item that never reached the target systems is removed without asking them
		final var reviewed = item(Kind.TILLSTAND, "4");
		assertThat(cleaner.clean(reviewed, MUNICIPALITY_ID, true).detail()).isEqualTo("Inget finns i målsystemen; raden i statustabellen tas bort");
	}

	@Test
	void whatIsRemovedBeforeAStepFailsIsClearedFromTheItem() {
		final var permit = permit();
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(migratedErrand(ERRAND_ID, "ALKT-TILLSTAND-1")));
		when(partyAssets.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(new Asset().id(ASSET_ID).assetId(ERRAND_ID)));
		when(relations.findRelationsFrom(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of(relation("to-asset", ERRAND_ID, ASSET_ID)));
		when(supportManagement.deleteErrand(MUNICIPALITY_ID, ERRAND_ID)).thenThrow(Problem.valueOf(CONFLICT, "Ärendet har ett beslut"));

		final var result = cleaner.clean(permit, MUNICIPALITY_ID, false);

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).startsWith("Rensningen stannade:");
		// The next cleanup goes on with the errand alone
		assertThat(permit.getPartyAssetsId()).isNull();
		assertThat(permit.getAssetId()).isNull();
		assertThat(permit.getErrandId()).isEqualTo(ERRAND_ID);
		verify(itemRepository).save(permit);
		verify(itemRepository, never()).delete(any());
	}

	private static MigrationItemEntity permit() {
		final var permit = item(Kind.TILLSTAND, "1");
		permit.setErrandType(ErrandType.SERVING_PERMIT);
		permit.setErrandId(ERRAND_ID);
		permit.setErrandNumber("ALKT-2026-1");
		permit.setAssetId(ERRAND_ID);
		permit.setPartyAssetsId(ASSET_ID);
		return permit;
	}

	private static MigrationItemEntity item(final Kind kind, final String sourceId) {
		final var item = new MigrationItemEntity();
		item.setId(Long.valueOf(sourceId));
		item.setSource(Source.ALKT);
		item.setKind(kind);
		item.setSourceId(sourceId);
		item.setMunicipalityId(MUNICIPALITY_ID);
		return item;
	}

	private static Errand migratedErrand(final String id, final String migrationId) {
		return new Errand().id(id).errandNumber("ALKT-2026-1").externalTags(Set.of(new ExternalTag().key("migreringsid").value(migrationId)));
	}

	private static Relation relation(final String id, final String source, final String target) {
		return new Relation().id(id).type("LINK").source(new ResourceIdentifier().resourceId(source)).target(new ResourceIdentifier().resourceId(target));
	}
}
