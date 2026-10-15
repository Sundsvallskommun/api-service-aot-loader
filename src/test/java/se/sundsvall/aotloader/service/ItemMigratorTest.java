package se.sundsvall.aotloader.service;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetAttachment;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.partyassets.Status;
import generated.se.sundsvall.relation.Relation;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Errand.LifecycleEnum;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.Investigation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.integration.db.MigrationItemRepository;
import se.sundsvall.aotloader.integration.db.ReviewExceptionRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationItemEntity;
import se.sundsvall.aotloader.integration.db.model.ReviewExceptionEntity;
import se.sundsvall.aotloader.integration.party.PartyIntegration;
import se.sundsvall.aotloader.integration.partyassets.PartyAssetsIntegration;
import se.sundsvall.aotloader.integration.relation.RelationIntegration;
import se.sundsvall.aotloader.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.aotloader.service.mapper.TargetMapper;
import se.sundsvall.aotloader.service.model.ExceptionOutcome;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.ItemStatus;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.PartyType;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.service.model.Step;
import se.sundsvall.aotloader.source.files.FileArea;
import se.sundsvall.aotloader.support.MigrationException;
import se.sundsvall.dept44.problem.Problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYN;
import static se.sundsvall.aotloader.service.model.ReviewRule.NO_ISSUING_ERRAND;
import static se.sundsvall.aotloader.service.model.ReviewRule.OWNER_DIFFERS;
import static se.sundsvall.aotloader.service.model.ReviewRule.PERMIT_ENDED;
import static se.sundsvall.aotloader.service.model.ReviewRule.SALES_PLACE_CEASED;

@ExtendWith(MockitoExtension.class)
class ItemMigratorTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String TIMRA = "2262";
	private static final String ERRAND_ID = "11111111-1111-1111-1111-111111111111";
	private static final String PERMIT_ERRAND_ID = "22222222-2222-2222-2222-222222222222";
	private static final String PARTY_ID = "33333333-3333-3333-3333-333333333333";
	private static final String PERSON_PARTY_ID = "44444444-4444-4444-4444-444444444444";
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T08:00:00Z"), ZoneId.of("Europe/Stockholm"));

	@Mock
	private MigrationItemRepository itemRepository;

	@Mock
	private ReviewExceptionRepository exceptionRepository;

	@Mock
	private PartyIntegration party;

	@Mock
	private SupportManagementIntegration supportManagement;

	@Mock
	private PartyAssetsIntegration partyAssets;

	@Mock
	private RelationIntegration relations;

	@Captor
	private ArgumentCaptor<Errand> errandCaptor;

	@Captor
	private ArgumentCaptor<AssetCreateRequest> assetCaptor;

	@Captor
	private ArgumentCaptor<AssetUpdateRequest> updateCaptor;

	@TempDir
	private Path documents;

	@TempDir
	private Path attachments;

	private final List<ItemStatus> savedStatuses = new ArrayList<>();
	private ItemMigrator migrator;
	private AotLoaderProperties properties;

	@BeforeEach
	void setUp() {
		properties = TestData.properties(documents.toString(), attachments.toString());
		migrator = migrator(properties);
		lenient().when(itemRepository.save(any())).thenAnswer(invocation -> {
			final MigrationItemEntity entity = invocation.getArgument(0);
			savedStatuses.add(entity.getStatus());
			return entity;
		});
		lenient().when(party.getPartyId(MUNICIPALITY_ID, TestData.HOLDER_ID)).thenReturn(Optional.of(PARTY_ID));
		lenient().when(party.getPartyId(MUNICIPALITY_ID, TestData.PERSON_ID)).thenReturn(Optional.of(PERSON_PARTY_ID));
	}

	@Test
	void anItemAlreadyMigratedIsLeftAsItIs() {
		final var candidate = TestData.permit("1");
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(item(ItemStatus.KLAR, ERRAND_ID)));

		final var result = migrator.process(candidate, context(false));

		assertThat(result).isEqualTo(new ItemResult(Outcome.REDAN_MIGRERAD, List.of(), null, "ALKT-2026-1"));
		verifyNoInteractions(party, supportManagement, partyAssets, relations, exceptionRepository);
		verify(itemRepository, never()).save(any());
	}

	@Test
	void aDryRunWritesNothing() throws IOException {
		Files.writeString(documents.resolve("beslut.rtf"), "dokument");
		final var candidate = TestData.permit("1", RuleHit.of(NO_ISSUING_ERRAND));

		final var result = migrator.process(candidate, context(true));

		assertThat(result.outcome()).isEqualTo(Outcome.SKULLE_MIGRERAS);
		assertThat(result.rules()).containsExactly("G13", "G14");
		assertThat(result.detail()).contains("G13 Tillståndet går inte att koppla", "G14 1 fil saknas på filarean (ansokan.pdf)");
		verify(party).getPartyId(MUNICIPALITY_ID, TestData.HOLDER_ID);
		verify(party).getPartyId(MUNICIPALITY_ID, TestData.PERSON_ID);
		verifyNoInteractions(supportManagement, partyAssets, relations);
		verify(itemRepository, never()).save(any());
	}

	@Test
	void anItemWithABlockingRuleHitIsReviewed() {
		final var candidate = TestData.permit("1", RuleHit.of(OWNER_DIFFERS, "AgarID 1, 2"), RuleHit.of(NO_ISSUING_ERRAND));
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(existing));

		final var result = migrator.process(candidate, context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.GRANSKAS);
		assertThat(result.rules()).containsExactly("G2", "G13");
		final var draftLeft = "Ärendet ALKT-2026-1 från en tidigare körning finns kvar i SupportManagement. Det slutförs om ärendet får migreras, annars hanteras det enligt avsnitt 10.3";
		assertThat(result.detail()).startsWith("G2 Tillståndets ägare skiljer sig från serveringsställets nuvarande ägare: AgarID 1, 2; " + draftLeft + "; G13");
		assertThat(result.errandNumber()).isEqualTo("ALKT-2026-1");
		assertThat(existing.getStatus()).isEqualTo(ItemStatus.GRANSKAS);
		assertThat(existing.getReason()).isEqualTo("G2 Tillståndets ägare skiljer sig från serveringsställets nuvarande ägare: AgarID 1, 2; " + draftLeft);
		assertThat(existing.getErrandId()).isEqualTo(ERRAND_ID);
		verifyNoInteractions(supportManagement, partyAssets, relations);
	}

	@Test
	void anItemThatStopsTellsWhatAnEarlierRunLeftInTheTargetSystems() {
		final var candidate = TestData.permit("1", RuleHit.of(SALES_PLACE_CEASED));
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		existing.setErrandNumber(null);
		existing.setPartyAssetsId("asset-internal-id");
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(existing));

		final var result = migrator.process(candidate, context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAS_INTE);
		assertThat(existing.getStatus()).isEqualTo(ItemStatus.MIGRERAS_INTE);
		assertThat(existing.getReason()).endsWith("; Ärendet " + ERRAND_ID
			+ " från en tidigare körning finns kvar i SupportManagement, och tillståndet i PartyAssets. Det slutförs om ärendet får migreras, annars hanteras det enligt avsnitt 10.3");
		verifyNoInteractions(supportManagement, partyAssets, relations);
	}

	@Test
	void anEndedPermitIsNotMigratedUnlessTheExceptionListSaysSo() {
		final var candidate = TestData.permit("1", RuleHit.of(SALES_PLACE_CEASED));

		final var notMigrated = migrator.process(candidate, context(true));
		assertThat(notMigrated.outcome()).isEqualTo(Outcome.MIGRERAS_INTE);

		final var exception = new ReviewExceptionEntity();
		exception.setOutcome(ExceptionOutcome.MIGRERA);
		when(exceptionRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(exception));

		final var migrated = migrator.process(candidate, context(true));
		assertThat(migrated.outcome()).isEqualTo(Outcome.SKULLE_MIGRERAS);
		assertThat(migrated.detail()).contains("Undantagslistan: MIGRERA");
	}

	@Test
	void aPermitHolderMissingInPartyIsReviewed() {
		when(party.getPartyId(MUNICIPALITY_ID, TestData.HOLDER_ID)).thenReturn(Optional.empty());
		when(party.getPartyId(MUNICIPALITY_ID, TestData.PERSON_ID)).thenReturn(Optional.empty());

		final var result = migrator.process(TestData.permit("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.GRANSKAS);
		assertThat(result.rules()).containsExactly("G10");
		assertThat(result.detail()).contains("G10 Tillståndshavaren finns inte i Party", "1 person(er) med betydande inflytande finns inte i Party");
		assertThat(savedStatuses).containsExactly(ItemStatus.GRANSKAS);
	}

	@Test
	void aPartyLookupThatFailsIsAnError() {
		when(party.getPartyId(MUNICIPALITY_ID, TestData.HOLDER_ID)).thenThrow(new MigrationException("Uppslaget i Party misslyckades (502 BAD_GATEWAY)"));

		final var result = migrator.process(TestData.permit("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).isEqualTo("Steg Tillståndshavare: Uppslaget i Party misslyckades (502 BAD_GATEWAY)");
		assertThat(savedStatuses).containsExactly(ItemStatus.FEL);
	}

	@Test
	void aNewPermitIsMigratedStepByStep() throws IOException {
		Files.writeString(documents.resolve("beslut.rtf"), "dokument");
		Files.writeString(attachments.resolve("ansokan.pdf"), "bilaga");
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of());
		when(supportManagement.createErrand(eq(MUNICIPALITY_ID), errandCaptor.capture())).thenReturn(ERRAND_ID);
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.getAttachments(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of(new ErrandAttachment().fileName("BESLUT.rtf").hash(FileUploader.sha256("dokument".getBytes(StandardCharsets.UTF_8)).toUpperCase())));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.empty());
		when(partyAssets.createAsset(eq(MUNICIPALITY_ID), eq("LINK|" + ERRAND_ID + ";case;supportmanagement;AOT|"), assetCaptor.capture())).thenReturn(Optional.of("asset-internal-id"));
		when(supportManagement.createAttachment(eq(MUNICIPALITY_ID), eq(ERRAND_ID), anyString(), any(), any())).thenReturn(Optional.of("attachment-id"));

		final var result = migrator.process(TestData.permit("1"), context(false));

		assertThat(result).isEqualTo(new ItemResult(Outcome.MIGRERAD, List.of(), null, "ALKT-2026-1"));
		assertThat(savedStatuses).containsExactly(ItemStatus.TILLSTANDSHAVARE_KLAR, ItemStatus.ARENDE_SKAPAT, ItemStatus.BILAGOR_KLARA, ItemStatus.TILLSTAND_SKAPAT, ItemStatus.KLAR);
		final var created = errandCaptor.getValue();
		assertThat(created.getLifecycle()).isEqualTo(LifecycleEnum.DRAFT);
		assertThat(created.getReporterUserId()).isEqualTo("alkt-migration");
		assertThat(created.getExternalTags()).extracting("key", "value").contains(tuple("migreringsid", "ALKT-TILLSTAND-1"));
		// The holder, its contact person, and the persons with significant influence
		assertThat(created.getStakeholders()).extracting("role", "externalId", "firstName").containsExactly(
			tuple("PRIMARY", PARTY_ID, null),
			tuple("CONTACT", null, "Kontakt Person"),
			tuple("CONTACT", PERSON_PARTY_ID, "Person Ett"),
			tuple("CONTACT", null, "Utländsk Person"));
		verify(supportManagement).createAttachment(eq(MUNICIPALITY_ID), eq(ERRAND_ID), eq("ansokan.pdf"), any(), any());
		assertThat(assetCaptor.getValue().getAssetId()).isEqualTo(ERRAND_ID);
		assertThat(assetCaptor.getValue().getStatus()).isEqualTo(Status.ACTIVE);
		final var order = inOrder(supportManagement, partyAssets);
		order.verify(supportManagement).createErrand(eq(MUNICIPALITY_ID), any());
		order.verify(supportManagement).createAttachment(eq(MUNICIPALITY_ID), eq(ERRAND_ID), anyString(), any(), any());
		order.verify(partyAssets).createAsset(eq(MUNICIPALITY_ID), anyString(), any());
		order.verify(supportManagement).updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), errandCaptor.capture());
		assertThat(errandCaptor.getValue()).isEqualTo(new Errand().lifecycle(LifecycleEnum.ACTIVE));
	}

	@Test
	void aPlaceInAnAreaOfAnotherMunicipalityIsMigratedToTheMunicipalityOfTheRunWithItsOwnAsParameter() {
		final var saved = ArgumentCaptor.forClass(MigrationItemEntity.class);
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of());
		when(supportManagement.createErrand(eq(MUNICIPALITY_ID), errandCaptor.capture())).thenReturn(ERRAND_ID);
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.getAttachments(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of());
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.empty());
		when(partyAssets.createAsset(eq(MUNICIPALITY_ID), anyString(), assetCaptor.capture())).thenReturn(Optional.of("asset-internal-id"));

		final var result = migrator.process(TestData.permit("1", "Timrå"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).startsWith("Stället hör till kommun 2262 (område Timrå)");
		assertThat(errandCaptor.getValue().getParameters()).filteredOn("key", "premisesMunicipalityId").extracting("values").containsExactly(List.of(TIMRA));
		assertThat(assetCaptor.getValue().getAdditionalParameters()).containsEntry("premisesMunicipalityId", TIMRA);
		verify(supportManagement).updateErrand(MUNICIPALITY_ID, ERRAND_ID, new Errand().lifecycle(LifecycleEnum.ACTIVE));
		verify(itemRepository, times(5)).save(saved.capture());
		assertThat(saved.getAllValues()).allSatisfy(entity -> assertThat(entity.getMunicipalityId()).isEqualTo(MUNICIPALITY_ID));
		verify(party, never()).getPartyId(eq(TIMRA), any());
		verify(supportManagement, never()).createErrand(eq(TIMRA), any());
	}

	@Test
	void anItemIsNeverMovedToAnotherMunicipality() {
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		existing.setMunicipalityId(TIMRA);
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(existing));

		final var result = migrator.process(TestData.permit("1", "Timrå"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).isEqualTo("Steg Läs och bedöm: Ärendet migrerades till kommun 2262, men körningen gäller kommun 2281. Det flyttas inte, eftersom det då skulle skapas på nytt.");
		assertThat(existing.getMunicipalityId()).isEqualTo(TIMRA);
		assertThat(existing.getErrandId()).isEqualTo(ERRAND_ID);
		verifyNoInteractions(party, supportManagement, partyAssets, relations);
	}

	@Test
	void anInterruptedItemContinuesWithItsDraft() {
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		existing.setFailedStep(Step.TILLSTAND);
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(existing));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(supportManagement.getAttachments(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of());
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.ACTIVE)));

		final var result = migrator.process(TestData.permit("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).isEqualTo("G14 2 filer saknas på filarean (beslut.rtf, ansokan.pdf)");
		assertThat(existing.getStatus()).isEqualTo(ItemStatus.KLAR);
		assertThat(existing.getFailedStep()).isNull();
		assertThat(existing.getReason()).isNull();
		assertThat(existing.getPartyAssetsId()).isEqualTo("asset-internal-id");
		verify(supportManagement, never()).findErrandsByExternalTag(anyString(), anyString(), anyString());
		verify(supportManagement, never()).createErrand(anyString(), any());
		verify(partyAssets, never()).createAsset(anyString(), anyString(), any());
		verify(partyAssets, never()).updateAsset(anyString(), anyString(), any());
		// The draft is updated with what the source says now, then activated
		verify(supportManagement, times(2)).updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), errandCaptor.capture());
		assertThat(errandCaptor.getAllValues().getFirst().getLifecycle()).isNull();
		// SupportManagement refuses a patch with a reporter
		assertThat(errandCaptor.getAllValues().getFirst().getReporterUserId()).isNull();
		assertThat(errandCaptor.getAllValues().getFirst().getTitle()).isEqualTo("Serveringstillstånd – Restaurang 1");
		assertThat(errandCaptor.getAllValues().get(1).getLifecycle()).isEqualTo(LifecycleEnum.ACTIVE);
	}

	@Test
	void anErrandThatIsGoneIsLookedUpByItsTagAndAnActiveOneIsLeftAsItIs() {
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(item(ItemStatus.ARENDE_SKAPAT, "gone")));
		when(supportManagement.getErrand(MUNICIPALITY_ID, "gone")).thenReturn(Optional.empty());
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.ACTIVE)));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.ACTIVE)));

		final var result = migrator.process(TestData.permit("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		verify(supportManagement, never()).updateErrand(anyString(), anyString(), any());
		verify(supportManagement, never()).getAttachments(anyString(), anyString());
		verify(supportManagement, never()).createAttachment(anyString(), anyString(), anyString(), any(), any());
	}

	@Test
	void anErrandThatIsGoneGetsItsPermitAndRelationAgain() {
		final var gone = item(ItemStatus.FEL, "gone-errand-id");
		gone.setFailedStep(Step.AKTIVERA);
		gone.setAssetId("gone-errand-id");
		gone.setPartyAssetsId("old-asset-id");
		gone.setRelationId("old-relation-id");
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(gone));
		when(supportManagement.getErrand(MUNICIPALITY_ID, "gone-errand-id")).thenReturn(Optional.empty());
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of());
		when(supportManagement.createErrand(eq(MUNICIPALITY_ID), any())).thenReturn(ERRAND_ID);
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.getAttachments(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of());
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.empty());
		when(partyAssets.createAsset(eq(MUNICIPALITY_ID), anyString(), assetCaptor.capture())).thenReturn(Optional.of("new-asset-id"));

		final var result = migrator.process(TestData.permit("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).contains(
			"Ärendet gone-errand-id fanns inte längre i SupportManagement och ersattes",
			"Tillståndet old-asset-id i PartyAssets hör till det gamla ärendet och ska tas bort",
			"Relationen old-relation-id hör till det gamla ärendet och ska tas bort");
		assertThat(assetCaptor.getValue().getAssetId()).isEqualTo(ERRAND_ID);
		assertThat(gone.getStatus()).isEqualTo(ItemStatus.KLAR);
		assertThat(gone.getErrandId()).isEqualTo(ERRAND_ID);
		assertThat(gone.getAssetId()).isEqualTo(ERRAND_ID);
		assertThat(gone.getPartyAssetsId()).isEqualTo("new-asset-id");
		assertThat(gone.getRelationId()).isNull();
	}

	@Test
	void severalErrandsWithTheSameTagStopTheItem() {
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.DRAFT), errand(LifecycleEnum.ACTIVE)));

		final var result = migrator.process(TestData.permit("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).isEqualTo("Steg Ärende: SupportManagement har 2 ärenden med migreringsid ALKT-TILLSTAND-1, inget nytt skapas");
		assertThat(savedStatuses).containsExactly(ItemStatus.TILLSTANDSHAVARE_KLAR, ItemStatus.FEL);
		verify(supportManagement, never()).createErrand(anyString(), any());
	}

	@Test
	void anErrandWithoutLifecycleStopsTheItem() {
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(null)));

		final var result = migrator.process(TestData.permit("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).contains("SupportManagement svarade utan livscykel");
	}

	@Test
	void aPermitThatExistsAfterAllIsReadAndOneThatCannotBeFoundIsAnError() {
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.ACTIVE)));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.empty()).thenReturn(Optional.of(assetAsCreated("found-after-409", Status.EXPIRED)));
		when(partyAssets.createAsset(eq(MUNICIPALITY_ID), anyString(), any())).thenReturn(Optional.empty());
		when(partyAssets.getAsset(MUNICIPALITY_ID, "found-after-409")).thenReturn(Optional.of(assetAsCreated("found-after-409", Status.ACTIVE)));

		final var found = migrator.process(TestData.permit("1"), context(false));
		assertThat(found.outcome()).isEqualTo(Outcome.MIGRERAD);
		// The item has no exception now, so the permit an earlier run made EXPIRED is to be ACTIVE
		verify(partyAssets).updateAsset(MUNICIPALITY_ID, "found-after-409", new AssetUpdateRequest().status(Status.ACTIVE));
		assertThat(found.detail()).isEqualTo("Tillståndet i PartyAssets hade status EXPIRED från en tidigare körning och fick status ACTIVE");

		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.empty());
		final var notFound = migrator.process(TestData.permit("1"), context(false));
		assertThat(notFound.outcome()).isEqualTo(Outcome.FEL);
		assertThat(notFound.detail()).isEqualTo("Steg Tillstånd i PartyAssets: PartyAssets svarade att tillståndet redan finns, men det gick inte att hitta");
	}

	@Test
	void anExpiredPermitAccordingToTheExceptionList() {
		final var exception = new ReviewExceptionEntity();
		exception.setOutcome(ExceptionOutcome.MIGRERA_SOM_UTGANGET);
		when(exceptionRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(exception));
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.ACTIVE)));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.empty());
		when(partyAssets.createAsset(eq(MUNICIPALITY_ID), anyString(), assetCaptor.capture())).thenReturn(Optional.of("id"));

		migrator.process(TestData.permit("1", RuleHit.of(PERMIT_ENDED)), context(false));

		assertThat(assetCaptor.getValue().getStatus()).isEqualTo(Status.EXPIRED);
	}

	@Test
	void anExpiredPermitGetsItsFilesWhileActiveAndIsExpiredAgain() throws IOException {
		Files.writeString(documents.resolve("beslut.rtf"), "dokument");
		expiredByTheExceptionList();
		when(partyAssets.createAsset(eq(MUNICIPALITY_ID), anyString(), assetCaptor.capture())).thenReturn(Optional.of("asset-internal-id"));
		when(partyAssets.getAttachments(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(List.of());
		when(partyAssets.createAttachment(MUNICIPALITY_ID, "asset-internal-id", "beslut.rtf", "dokument".getBytes(StandardCharsets.UTF_8), "Tillståndsbevis")).thenReturn(true);
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id"))
			.thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.ACTIVE)))
			.thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.EXPIRED)));

		final var result = migrator.process(TestData.permitWithCertificate("1", RuleHit.of(PERMIT_ENDED)), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(assetCaptor.getValue().getStatus()).isEqualTo(Status.EXPIRED);
		final var order = inOrder(partyAssets);
		order.verify(partyAssets).updateAsset(MUNICIPALITY_ID, "asset-internal-id", new AssetUpdateRequest().status(Status.ACTIVE));
		order.verify(partyAssets).createAttachment(MUNICIPALITY_ID, "asset-internal-id", "beslut.rtf", "dokument".getBytes(StandardCharsets.UTF_8), "Tillståndsbevis");
		order.verify(partyAssets).updateAsset(MUNICIPALITY_ID, "asset-internal-id", new AssetUpdateRequest().status(Status.EXPIRED));
		assertThat(result.detail()).contains("Tillståndet i PartyAssets hade status ACTIVE medan det fick sina filer, eftersom ett tillstånd med status EXPIRED inte tar emot filer",
			"Tillståndet i PartyAssets har 1 av 1 filer: Tillståndsbevis 1", "Tillståndet i PartyAssets fick status EXPIRED igen när det hade fått sina filer");
	}

	@Test
	void anExpiredPermitThatHasItsFilesStaysExpired() throws IOException {
		Files.writeString(documents.resolve("beslut.rtf"), "dokument");
		expiredByTheExceptionList();
		when(partyAssets.createAsset(eq(MUNICIPALITY_ID), anyString(), any())).thenReturn(Optional.of("asset-internal-id"));
		when(partyAssets.getAttachments(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(List.of(new AssetAttachment().fileName("BESLUT.rtf").fileSize("dokument".length())));

		final var result = migrator.process(TestData.permitWithCertificate("1", RuleHit.of(PERMIT_ENDED)), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		verify(partyAssets, never()).updateAsset(anyString(), anyString(), any());
		verify(partyAssets, never()).createAttachment(anyString(), anyString(), anyString(), any(), anyString());
	}

	@Test
	void aPermitWhosePostDoesNotBecomeExpiredAgainFails() throws IOException {
		Files.writeString(documents.resolve("beslut.rtf"), "dokument");
		expiredByTheExceptionList();
		when(partyAssets.createAsset(eq(MUNICIPALITY_ID), anyString(), any())).thenReturn(Optional.of("asset-internal-id"));
		when(partyAssets.getAttachments(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(List.of());
		when(partyAssets.createAttachment(MUNICIPALITY_ID, "asset-internal-id", "beslut.rtf", "dokument".getBytes(StandardCharsets.UTF_8), "Tillståndsbevis")).thenReturn(true);
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.ACTIVE)));

		final var result = migrator.process(TestData.permitWithCertificate("1", RuleHit.of(PERMIT_ENDED)), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).startsWith("Steg Tillstånd i PartyAssets: Tillståndet asset-internal-id i PartyAssets fick inte status EXPIRED");
	}

	@Test
	void anExpiredPermitWhoseFilesFailIsExpiredAgainBeforeTheStepFails() throws IOException {
		Files.writeString(documents.resolve("beslut.rtf"), "dokument");
		expiredByTheExceptionList();
		when(partyAssets.createAsset(eq(MUNICIPALITY_ID), anyString(), any())).thenReturn(Optional.of("asset-internal-id"));
		when(partyAssets.getAttachments(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(List.of());
		when(partyAssets.createAttachment(eq(MUNICIPALITY_ID), eq("asset-internal-id"), anyString(), any(), anyString())).thenThrow(Problem.valueOf(BAD_GATEWAY, "Tjänsten svarar inte"));
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id"))
			.thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.ACTIVE)))
			.thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.EXPIRED)));

		final var result = migrator.process(TestData.permitWithCertificate("1", RuleHit.of(PERMIT_ENDED)), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).startsWith("Steg Tillstånd i PartyAssets: 502 BAD_GATEWAY Tjänsten svarar inte")
			.doesNotContain("fast det ska vara EXPIRED");
		final var order = inOrder(partyAssets);
		order.verify(partyAssets).updateAsset(MUNICIPALITY_ID, "asset-internal-id", new AssetUpdateRequest().status(Status.ACTIVE));
		order.verify(partyAssets).createAttachment(eq(MUNICIPALITY_ID), eq("asset-internal-id"), anyString(), any(), anyString());
		order.verify(partyAssets).updateAsset(MUNICIPALITY_ID, "asset-internal-id", new AssetUpdateRequest().status(Status.EXPIRED));
	}

	@Test
	void anExpiredPermitWhoseFilesFailAndThatIsNotExpiredAgainIsReportedActive() throws IOException {
		Files.writeString(documents.resolve("beslut.rtf"), "dokument");
		expiredByTheExceptionList();
		when(partyAssets.createAsset(eq(MUNICIPALITY_ID), anyString(), any())).thenReturn(Optional.of("asset-internal-id"));
		when(partyAssets.getAttachments(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(List.of());
		when(partyAssets.createAttachment(eq(MUNICIPALITY_ID), eq("asset-internal-id"), anyString(), any(), anyString())).thenThrow(Problem.valueOf(BAD_GATEWAY, "Tjänsten svarar inte"));
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.ACTIVE)));

		final var result = migrator.process(TestData.permitWithCertificate("1", RuleHit.of(PERMIT_ENDED)), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).startsWith("Steg Tillstånd i PartyAssets: 502 BAD_GATEWAY Tjänsten svarar inte")
			.contains("Tillståndet i PartyAssets är ACTIVE, fast det ska vara EXPIRED, tills nästa körning ger det sina filer");
		verify(partyAssets).updateAsset(MUNICIPALITY_ID, "asset-internal-id", new AssetUpdateRequest().status(Status.EXPIRED));
	}

	/**
	 * Permit 1 is to be migrated as expired, its errand is active and its post does not exist yet.
	 */
	private void expiredByTheExceptionList() {
		final var exception = new ReviewExceptionEntity();
		exception.setOutcome(ExceptionOutcome.MIGRERA_SOM_UTGANGET);
		when(exceptionRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(exception));
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.ACTIVE)));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.empty());
	}

	@Test
	void thePermitOfAnEarlierRunGetsTheStatusTheExceptionListGivesNow() {
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		existing.setFailedStep(Step.AKTIVERA);
		existing.setPartyAssetsId("asset-internal-id");
		final var exception = new ReviewExceptionEntity();
		exception.setOutcome(ExceptionOutcome.MIGRERA_SOM_UTGANGET);
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(existing));
		when(exceptionRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(exception));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(supportManagement.getAttachments(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of());
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id"))
			.thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.ACTIVE)))
			.thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.EXPIRED)));

		final var result = migrator.process(TestData.permit("1", RuleHit.of(PERMIT_ENDED)), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).contains("Tillståndet i PartyAssets hade status ACTIVE från en tidigare körning och fick status EXPIRED");
		assertThat(existing.getStatus()).isEqualTo(ItemStatus.KLAR);
		assertThat(existing.getPartyAssetsId()).isEqualTo("asset-internal-id");
		verify(partyAssets).updateAsset(MUNICIPALITY_ID, "asset-internal-id", new AssetUpdateRequest().status(Status.EXPIRED));
		verify(partyAssets, never()).findAsset(anyString(), anyString(), anyString());
		verify(partyAssets, never()).createAsset(anyString(), anyString(), any());
	}

	@Test
	void aPermitGoneFromPartyAssetsIsCreatedAgain() {
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		existing.setFailedStep(Step.AKTIVERA);
		existing.setPartyAssetsId("gone-asset-id");
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(existing));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.ACTIVE)));
		when(partyAssets.getAsset(MUNICIPALITY_ID, "gone-asset-id")).thenReturn(Optional.empty());
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.empty());
		when(partyAssets.createAsset(eq(MUNICIPALITY_ID), anyString(), assetCaptor.capture())).thenReturn(Optional.of("new-asset-id"));

		final var result = migrator.process(TestData.permit("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).contains("Tillståndet gone-asset-id fanns inte längre i PartyAssets");
		assertThat(assetCaptor.getValue().getStatus()).isEqualTo(Status.ACTIVE);
		assertThat(existing.getPartyAssetsId()).isEqualTo("new-asset-id");
	}

	@Test
	void aPermitWithAStatusTheMigrationNeverGivesIsLeftAsItIs() {
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.ACTIVE)));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.of(new Asset().id("blocked-asset-id").status(Status.BLOCKED)));

		final var result = migrator.process(TestData.permit("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).isEqualTo("Steg Tillstånd i PartyAssets: Tillståndet blocked-asset-id i PartyAssets har status BLOCKED, som migreringen aldrig ger, och ändras inte");
		verify(partyAssets, never()).updateAsset(anyString(), anyString(), any());
	}

	@Test
	void thePermitOfAnEarlierRunGetsTheContentTheSourceGivesNow() {
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		existing.setFailedStep(Step.AKTIVERA);
		existing.setPartyAssetsId("asset-internal-id");
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(existing));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.ACTIVE)));
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id"))
			.thenReturn(Optional.of(staleAsset("asset-internal-id", Status.ACTIVE)))
			.thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.ACTIVE)));

		final var result = migrator.process(TestData.permit("1"), context(false));

		final var wanted = new TargetMapper(properties, CLOCK).toAsset(TestData.permit("1"), MUNICIPALITY_ID, ERRAND_ID, PARTY_ID, false);
		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).isEqualTo("Tillståndet i PartyAssets från en tidigare körning fick titel, giltighet och parametrar som källan ger nu");
		// The source gives the permit no last day, so the old one is removed
		verify(partyAssets).updateAsset(MUNICIPALITY_ID, "asset-internal-id", new AssetUpdateRequest().title(wanted.getTitle()).indefinitely(true).additionalParameters(wanted.getAdditionalParameters()));
	}

	@Test
	void aChangeThatPartyAssetsDoesNotMakeFailsTheStep() {
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		existing.setFailedStep(Step.AKTIVERA);
		existing.setPartyAssetsId("asset-internal-id");
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(existing));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.ACTIVE)));
		// PartyAssets before 6.8 answers the change with 204 and keeps the content
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(Optional.of(staleAsset("asset-internal-id", Status.ACTIVE)));

		final var result = migrator.process(TestData.permit("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).isEqualTo("Steg Tillstånd i PartyAssets: PartyAssets gjorde inte hela ändringen av tillståndet asset-internal-id, vilket kräver PartyAssets 6.8 eller senare");
		assertThat(existing.getStatus()).isEqualTo(ItemStatus.FEL);
		assertThat(existing.getFailedStep()).isEqualTo(Step.TILLSTAND);
	}

	@Test
	void aPostOfAnotherPartyIsLeftAsItIs() {
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		existing.setFailedStep(Step.AKTIVERA);
		existing.setPartyAssetsId("asset-internal-id");
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(existing));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.ACTIVE)));
		// The holder's number was corrected in the source after the earlier run made the post
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(Optional.of(staleAsset("asset-internal-id", Status.ACTIVE).partyId("another-party-id")));

		final var result = migrator.process(TestData.permit("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).isEqualTo("Steg Tillstånd i PartyAssets: Tillståndet asset-internal-id i PartyAssets tillhör en annan part än den källan anger nu och ändras inte (avsnitt 10.3)");
		verify(partyAssets, never()).updateAsset(anyString(), anyString(), any());
	}

	@Test
	void anActivePermitThatIsToExpireGetsItsContentBeforeItsStatus() {
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		existing.setFailedStep(Step.AKTIVERA);
		existing.setPartyAssetsId("asset-internal-id");
		final var exception = new ReviewExceptionEntity();
		exception.setOutcome(ExceptionOutcome.MIGRERA_SOM_UTGANGET);
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(existing));
		when(exceptionRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(exception));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.ACTIVE)));
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id"))
			.thenReturn(Optional.of(staleAsset("asset-internal-id", Status.ACTIVE)))
			.thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.ACTIVE)))
			.thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.EXPIRED)));

		final var result = migrator.process(TestData.permit("1", RuleHit.of(PERMIT_ENDED)), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).contains("Tillståndet i PartyAssets hade status ACTIVE från en tidigare körning och fick status EXPIRED",
			"Tillståndet i PartyAssets från en tidigare körning fick titel, giltighet och parametrar som källan ger nu");
		// An EXPIRED post can no longer get the content
		final var order = inOrder(partyAssets);
		order.verify(partyAssets).updateAsset(eq(MUNICIPALITY_ID), eq("asset-internal-id"), updateCaptor.capture());
		assertThat(updateCaptor.getValue().getStatus()).isNull();
		assertThat(updateCaptor.getValue().getTitle()).isNotNull();
		order.verify(partyAssets).getAsset(MUNICIPALITY_ID, "asset-internal-id");
		order.verify(partyAssets).updateAsset(MUNICIPALITY_ID, "asset-internal-id", new AssetUpdateRequest().status(Status.EXPIRED));
	}

	@Test
	void aContentChangeThatPartyAssetsIgnoresStopsBeforeThePostExpires() {
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		existing.setFailedStep(Step.AKTIVERA);
		existing.setPartyAssetsId("asset-internal-id");
		final var exception = new ReviewExceptionEntity();
		exception.setOutcome(ExceptionOutcome.MIGRERA_SOM_UTGANGET);
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(existing));
		when(exceptionRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(exception));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.ACTIVE)));
		// PartyAssets before 6.8 answers the change with 204 and keeps the content
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(Optional.of(staleAsset("asset-internal-id", Status.ACTIVE)));

		final var result = migrator.process(TestData.permit("1", RuleHit.of(PERMIT_ENDED)), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).isEqualTo("Steg Tillstånd i PartyAssets: PartyAssets gjorde inte hela ändringen av tillståndet asset-internal-id, vilket kräver PartyAssets 6.8 eller senare; "
			+ "Undantagslistan: MIGRERA_SOM_UTGANGET");
		verify(partyAssets, never()).updateAsset(MUNICIPALITY_ID, "asset-internal-id", new AssetUpdateRequest().status(Status.EXPIRED));
	}

	@Test
	void aPostGoneRightAfterItsChangeFailsTheStep() {
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		existing.setFailedStep(Step.AKTIVERA);
		existing.setPartyAssetsId("asset-internal-id");
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(existing));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.ACTIVE)));
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(Optional.of(staleAsset("asset-internal-id", Status.ACTIVE))).thenReturn(Optional.empty());

		final var result = migrator.process(TestData.permit("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).isEqualTo("Steg Tillstånd i PartyAssets: Tillståndet asset-internal-id fanns inte i PartyAssets efter ändringen");
	}

	@Test
	void anExpiredPermitThatIsToBeActiveGetsItsStatusBeforeItsContent() {
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.ACTIVE)));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.of(staleAsset("stale-asset-id", Status.EXPIRED)));
		when(partyAssets.getAsset(MUNICIPALITY_ID, "stale-asset-id")).thenReturn(Optional.of(assetAsCreated("stale-asset-id", Status.ACTIVE)));

		final var result = migrator.process(TestData.permit("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).isEqualTo("Tillståndet i PartyAssets hade status EXPIRED från en tidigare körning och fick status ACTIVE; "
			+ "Tillståndet i PartyAssets från en tidigare körning fick titel, giltighet och parametrar som källan ger nu");
		// PartyAssets changes the content of an ACTIVE post only
		final var order = inOrder(partyAssets);
		order.verify(partyAssets).updateAsset(MUNICIPALITY_ID, "stale-asset-id", new AssetUpdateRequest().status(Status.ACTIVE));
		order.verify(partyAssets).updateAsset(eq(MUNICIPALITY_ID), eq("stale-asset-id"), updateCaptor.capture());
		assertThat(updateCaptor.getValue().getStatus()).isNull();
		assertThat(updateCaptor.getValue().getTitle()).isNotNull();
	}

	@Test
	void anExpiredPermitThatStaysExpiredKeepsItsContent() {
		final var exception = new ReviewExceptionEntity();
		exception.setOutcome(ExceptionOutcome.MIGRERA_SOM_UTGANGET);
		when(exceptionRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(exception));
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.ACTIVE)));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.of(staleAsset("stale-asset-id", Status.EXPIRED)));

		final var result = migrator.process(TestData.permit("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).contains("Tillståndet i PartyAssets är EXPIRED, och dess titel, giltighet och parametrar kan därför inte få det källan ger nu");
		verify(partyAssets, never()).updateAsset(anyString(), anyString(), any());
	}

	@Test
	void whatPartyAssetsCannotChangeIsReported() {
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.ACTIVE)));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID))
			.thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.ACTIVE).type("OldPermitType").issued(LocalDate.of(2018, 1, 1))));

		final var result = migrator.process(TestData.permit("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).isEqualTo("Tillståndets typ, utfärdandedatum i PartyAssets skiljer sig från det källan ger nu och kan inte ändras där (avsnitt 10.3)");
		verify(partyAssets, never()).updateAsset(anyString(), anyString(), any());
	}

	@Test
	void filesThatCannotBeUploadedForGoodAreListedAndOtherFailuresFailTheStep() throws IOException {
		Files.writeString(documents.resolve("beslut.rtf"), "x".repeat(101));
		Files.writeString(attachments.resolve("ansokan.pdf"), "bilaga");
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(supportManagement.getAttachments(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of());
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.of(assetAsCreated("id", Status.ACTIVE)));
		when(supportManagement.createAttachment(eq(MUNICIPALITY_ID), eq(ERRAND_ID), anyString(), any(), any())).thenReturn(Optional.empty());

		final var rejected = migrator.process(TestData.permit("1"), context(false));

		assertThat(rejected.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(rejected.rules()).containsExactly("G14");
		assertThat(rejected.detail()).isEqualTo("G14 1 fil är större än 0 MB och laddas inte upp (beslut.rtf); G14 1 fil togs inte emot av SupportManagement (ansokan.pdf)");

		when(supportManagement.createAttachment(eq(MUNICIPALITY_ID), eq(ERRAND_ID), anyString(), any(), any())).thenThrow(Problem.valueOf(BAD_GATEWAY, "Tjänsten svarar inte"));

		final var failed = migrator.process(TestData.permit("1"), context(false));

		assertThat(failed.outcome()).isEqualTo(Outcome.FEL);
		assertThat(failed.detail()).startsWith("Steg Bilagor: 502 BAD_GATEWAY Tjänsten svarar inte");
	}

	@Test
	void aFileAreaThatCannotBeReadFailsTheStep() {
		properties = TestData.properties(documents.resolve("finns-inte").toString(), attachments.toString());
		migrator = migrator(properties);
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(supportManagement.getAttachments(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of());

		final var result = migrator.process(TestData.permit("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).isEqualTo("Steg Bilagor: Filarean för AlkT gick inte att läsa");
	}

	@Test
	void theLimitStopsTheItemBeforeItIsWritten() {
		final var context = new RunContext("run-id", MUNICIPALITY_ID, false, Map.of(MUNICIPALITY_ID, TestData.labelIds()), Map.of(MUNICIPALITY_ID, TestData.purposes()), 0, new AtomicBoolean(), this::fileArea);

		final var result = migrator.process(TestData.permit("1"), context);

		assertThat(result.outcome()).isEqualTo(Outcome.EJ_KORD);
		verify(itemRepository, never()).save(any());
		verifyNoInteractions(supportManagement);
	}

	@Test
	void anInspectionErrandWaitsForThePermitOfItsPlace() {
		final var permitKey = new ItemKey(Source.ALKT, TILLSTAND, "1");
		lenient().when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(item(ItemStatus.GRANSKAS, null)));

		final var result = migrator.process(TestData.inspection("700", permitKey, TestData.HOLDER_ID), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.VANTAR);
		assertThat(result.detail()).isEqualTo("Väntar på tillståndet ALKT/TILLSTAND/1, som är GRANSKAS");
		assertThat(savedStatuses).containsExactly(ItemStatus.VANTAR);
	}

	@Test
	void anInspectionErrandGetsItsRelation() {
		final var permitKey = new ItemKey(Source.ALKT, TILLSTAND, "1");
		lenient().when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(item(ItemStatus.KLAR, PERMIT_ERRAND_ID)));
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSYN-700")).thenReturn(List.of());
		when(supportManagement.createErrand(eq(MUNICIPALITY_ID), errandCaptor.capture())).thenReturn(ERRAND_ID);
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));
		when(relations.findRelations(MUNICIPALITY_ID, "LINK", PERMIT_ERRAND_ID, ERRAND_ID)).thenReturn(List.of());
		when(relations.createRelation(eq(MUNICIPALITY_ID), any())).thenReturn("relation-id");

		final var result = migrator.process(TestData.inspection("700", permitKey, TestData.HOLDER_ID), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).isEqualTo("Relation till tillståndsärendet ALKT/TILLSTAND/1");
		assertThat(savedStatuses).containsExactly(ItemStatus.TILLSTANDSHAVARE_KLAR, ItemStatus.ARENDE_SKAPAT, ItemStatus.BILAGOR_KLARA, ItemStatus.RELATION_KLAR, ItemStatus.KLAR);
		final var relation = ArgumentCaptor.forClass(Relation.class);
		verify(relations).createRelation(eq(MUNICIPALITY_ID), relation.capture());
		assertThat(relation.getValue().getSource().getResourceId()).isEqualTo(PERMIT_ERRAND_ID);
		assertThat(relation.getValue().getTarget().getResourceId()).isEqualTo(ERRAND_ID);
		assertThat(errandCaptor.getValue().getStakeholders()).extracting("role").containsExactly("INSPECTED");
		final var investigation = ArgumentCaptor.forClass(Investigation.class);
		verify(supportManagement).createInvestigation(eq(MUNICIPALITY_ID), eq(ERRAND_ID), investigation.capture());
		assertThat(investigation.getValue().getStatus()).isEqualTo("COMPLETED");
		assertThat(investigation.getValue().getStartedAt()).isEqualTo(OffsetDateTime.parse("2020-01-01T00:00+01:00"));
		assertThat(investigation.getValue().getCompletedAt()).isEqualTo(OffsetDateTime.parse("2020-03-01T00:00+01:00"));
		verifyNoInteractions(partyAssets);
	}

	@Test
	void anInvestigationAnEarlierRunCreatedIsNotCreatedAgain() {
		final var permitKey = new ItemKey(Source.ALKT, TILLSTAND, "1");
		lenient().when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(item(ItemStatus.KLAR, PERMIT_ERRAND_ID)));
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSYN-700")).thenReturn(List.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(supportManagement.getInvestigations(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of(new Investigation().id("investigation-id")));
		when(relations.findRelations(MUNICIPALITY_ID, "LINK", PERMIT_ERRAND_ID, ERRAND_ID)).thenReturn(List.of(new Relation().id("existing")));

		assertThat(migrator.process(TestData.inspection("700", permitKey, TestData.HOLDER_ID), context(false)).outcome()).isEqualTo(Outcome.MIGRERAD);

		verify(supportManagement, never()).createInvestigation(anyString(), anyString(), any());
	}

	@Test
	void anExistingRelationIsReusedAndSeveralAreReported() {
		final var permitKey = new ItemKey(Source.ALKT, TILLSTAND, "1");
		lenient().when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(item(ItemStatus.KLAR, PERMIT_ERRAND_ID)));
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSYN-700")).thenReturn(List.of(errand(LifecycleEnum.ACTIVE)));
		when(relations.findRelations(MUNICIPALITY_ID, "LINK", PERMIT_ERRAND_ID, ERRAND_ID)).thenReturn(List.of(new Relation().id("existing")));

		assertThat(migrator.process(TestData.inspection("700", permitKey, TestData.HOLDER_ID), context(false)).outcome()).isEqualTo(Outcome.MIGRERAD);

		when(relations.findRelations(MUNICIPALITY_ID, "LINK", PERMIT_ERRAND_ID, ERRAND_ID)).thenReturn(List.of(new Relation().id("a"), new Relation().id("b")));

		final var result = migrator.process(TestData.inspection("700", permitKey, TestData.HOLDER_ID), context(false));
		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).contains("Avvikelse: 2 relationer finns redan");
		verify(relations, never()).createRelation(anyString(), any());
	}

	@Test
	void inspectionErrandsWithoutRelation() {
		final var permitKey = new ItemKey(Source.ALKT, TILLSTAND, "1");
		final var formerHolder = new LegalId("5599887766", PartyType.ENTERPRISE);
		when(party.getPartyId(MUNICIPALITY_ID, formerHolder)).thenReturn(Optional.empty());

		assertThat(migrator.process(TestData.inspection("700", null, TestData.HOLDER_ID), context(true)).detail()).isEqualTo("Ingen relation: stället har inget gällande tillstånd");

		lenient().when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(item(ItemStatus.MIGRERAS_INTE, null)));
		assertThat(migrator.process(TestData.inspection("701", permitKey, TestData.HOLDER_ID), context(true)).detail()).isEqualTo("Ingen relation: tillståndet migreras inte");

		lenient().when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(item(ItemStatus.KLAR, PERMIT_ERRAND_ID)));
		final var former = migrator.process(TestData.inspection("702", permitKey, formerHolder), context(true));
		assertThat(former.outcome()).isEqualTo(Outcome.SKULLE_MIGRERAS);
		assertThat(former.rules()).containsExactly("G10");
		assertThat(former.detail()).contains("G10 Tillståndshavaren som ärendet gällde finns inte i Party", "Ingen relation: ärendet gällde en tidigare tillståndshavare");
		assertThat(migrator.process(TestData.inspection("703", permitKey, null), context(true)).detail()).contains("Ingen relation: det går inte att se vilken tillståndshavare ärendet gällde");
	}

	@Test
	void aDryRunOfAnInspectionErrandCountsOnThePermitOfTheSameRun() {
		final var permitKey = new ItemKey(Source.ALKT, TILLSTAND, "1");
		final var context = context(true);
		context.recordOutcome(permitKey, Outcome.SKULLE_MIGRERAS);

		final var result = migrator.process(TestData.inspection("700", permitKey, TestData.HOLDER_ID), context);

		assertThat(result.outcome()).isEqualTo(Outcome.SKULLE_MIGRERAS);
		assertThat(result.detail()).isEqualTo("Relation till tillståndsärendet ALKT/TILLSTAND/1");
		verifyNoInteractions(relations, supportManagement);
	}

	@Test
	void aMigratedInspectionErrandGetsItsRelationWhenItsPermitIsMigratedAfterIt() {
		final var permitKey = new ItemKey(Source.ALKT, TILLSTAND, "1");
		final var inspection = item(ItemStatus.KLAR, ERRAND_ID);
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSYN, "700")).thenReturn(Optional.of(inspection));
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(item(ItemStatus.KLAR, PERMIT_ERRAND_ID)));
		when(relations.findRelations(MUNICIPALITY_ID, "LINK", PERMIT_ERRAND_ID, ERRAND_ID)).thenReturn(List.of());
		when(relations.createRelation(eq(MUNICIPALITY_ID), any())).thenReturn("relation-id");

		final var result = migrator.process(TestData.inspection("700", permitKey, TestData.HOLDER_ID), context(false));

		assertThat(result).isEqualTo(new ItemResult(Outcome.REDAN_MIGRERAD, List.of(), "Relation till tillståndsärendet ALKT/TILLSTAND/1, tillagd i efterhand", "ALKT-2026-1"));
		assertThat(inspection.getRelationId()).isEqualTo("relation-id");
		assertThat(savedStatuses).containsExactly(ItemStatus.KLAR);
		verifyNoInteractions(party, supportManagement, partyAssets);

		// With the relation in place, nothing more is done
		final var again = migrator.process(TestData.inspection("700", permitKey, TestData.HOLDER_ID), context(false));

		assertThat(again).isEqualTo(new ItemResult(Outcome.REDAN_MIGRERAD, List.of(), null, "ALKT-2026-1"));
		verify(relations).createRelation(anyString(), any());
	}

	@Test
	void aMigratedInspectionErrandIsLeftAsItIsWhenItsPermitIsNotMigratedOrInADryRun() {
		final var permitKey = new ItemKey(Source.ALKT, TILLSTAND, "1");
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSYN, "700")).thenReturn(Optional.of(item(ItemStatus.KLAR, ERRAND_ID)));
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(item(ItemStatus.MIGRERAS_INTE, null)));

		assertThat(migrator.process(TestData.inspection("700", permitKey, TestData.HOLDER_ID), context(false)).detail()).isNull();
		assertThat(migrator.process(TestData.inspection("700", permitKey, TestData.HOLDER_ID), context(true)).outcome()).isEqualTo(Outcome.REDAN_MIGRERAD);
		verifyNoInteractions(relations);
		verify(itemRepository, never()).save(any());
	}

	@Test
	void aRelationAddedAfterwardsThatFailsIsReportedAndTheItemStaysMigrated() {
		final var permitKey = new ItemKey(Source.ALKT, TILLSTAND, "1");
		final var inspection = item(ItemStatus.KLAR, ERRAND_ID);
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSYN, "700")).thenReturn(Optional.of(inspection));
		when(itemRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(item(ItemStatus.KLAR, PERMIT_ERRAND_ID)));
		when(relations.findRelations(MUNICIPALITY_ID, "LINK", PERMIT_ERRAND_ID, ERRAND_ID)).thenThrow(Problem.valueOf(BAD_GATEWAY, "Tjänsten svarar inte"))
			.thenReturn(List.of(new Relation().id("a"), new Relation().id("b")));

		final var failed = migrator.process(TestData.inspection("700", permitKey, TestData.HOLDER_ID), context(false));

		assertThat(failed.outcome()).isEqualTo(Outcome.FEL);
		assertThat(failed.detail()).isEqualTo("Steg Relation till tillståndsärendet: 502 BAD_GATEWAY Tjänsten svarar inte");
		assertThat(inspection.getStatus()).isEqualTo(ItemStatus.KLAR);
		verify(itemRepository, never()).save(any());

		// On the next run several relations are there: the deviation is reported and none is created
		final var deviation = migrator.process(TestData.inspection("700", permitKey, TestData.HOLDER_ID), context(false));

		assertThat(deviation.outcome()).isEqualTo(Outcome.REDAN_MIGRERAD);
		assertThat(deviation.detail()).startsWith("Avvikelse: 2 relationer finns redan");
		verify(relations, never()).createRelation(anyString(), any());
		verify(itemRepository, never()).save(any());
	}

	private ItemMigrator migrator(final AotLoaderProperties settings) {
		return new ItemMigrator(itemRepository, exceptionRepository, new PartyResolver(party), new RelationPlanner(itemRepository), new FileUploader(supportManagement, partyAssets, settings, CLOCK),
			new TargetWriter(supportManagement, partyAssets, relations, new TargetMapper(settings, CLOCK)), settings, CLOCK);
	}

	/**
	 * @return the post of permit 1 as the migration creates it
	 */
	private Asset assetAsCreated(final String id, final Status status) {
		final var created = new TargetMapper(properties, CLOCK).toAsset(TestData.permit("1"), MUNICIPALITY_ID, ERRAND_ID, PARTY_ID, false);
		return new Asset().id(id).assetId(created.getAssetId()).partyId(created.getPartyId()).type(created.getType()).title(created.getTitle()).description(created.getDescription())
			.issued(created.getIssued()).validTo(created.getValidTo()).additionalParameters(created.getAdditionalParameters()).status(status);
	}

	/**
	 * @return the post of permit 1 as an earlier run created it, from what the source said then
	 */
	private Asset staleAsset(final String id, final Status status) {
		return assetAsCreated(id, status).title("Gammal titel").validTo(LocalDate.of(2020, 12, 31)).additionalParameters(Map.of("errandId", ERRAND_ID));
	}

	private RunContext context(final boolean dryRun) {
		return new RunContext("run-id", MUNICIPALITY_ID, dryRun, Map.of(MUNICIPALITY_ID, TestData.labelIds(), TIMRA, TestData.labelIds()), Map.of(MUNICIPALITY_ID, TestData.purposes(), TIMRA, TestData.purposes()), null, new AtomicBoolean(), this::fileArea);
	}

	private FileArea fileArea(final Source source) {
		return new FileArea(properties.fileArea(source), properties.maxFileSize().toBytes());
	}

	private static MigrationItemEntity item(final ItemStatus status, final String errandId) {
		final var item = new MigrationItemEntity();
		item.setStatus(status);
		item.setErrandId(errandId);
		item.setErrandNumber("ALKT-2026-1");
		return item;
	}

	private static Errand errand(final LifecycleEnum lifecycle) {
		return new Errand().id(ERRAND_ID).errandNumber("ALKT-2026-1").lifecycle(lifecycle);
	}

}
