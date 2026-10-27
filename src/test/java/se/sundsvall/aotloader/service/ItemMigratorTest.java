package se.sundsvall.aotloader.service;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetAttachment;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.partyassets.Status;
import generated.se.sundsvall.relation.Relation;
import generated.se.sundsvall.relation.ResourceIdentifier;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.DecisionTerm;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Errand.LifecycleEnum;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandLabel;
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
import se.sundsvall.aotloader.service.mapper.AssetMapper;
import se.sundsvall.aotloader.service.mapper.TargetMapper;
import se.sundsvall.aotloader.service.model.AssetData;
import se.sundsvall.aotloader.service.model.ExceptionOutcome;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.ItemStatus;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.PartyType;
import se.sundsvall.aotloader.service.model.PermitLink;
import se.sundsvall.aotloader.service.model.Person;
import se.sundsvall.aotloader.service.model.PlacePermit;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;
import static se.sundsvall.aotloader.service.model.Kind.ANMALAN;
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
	private static final String EXPIRED_LAST = "Tillståndet i PartyAssets fick status EXPIRED sist, när det hade fått sitt innehåll och sina filer";
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
	private ArgumentCaptor<Decision> decisionCaptor;

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
	void aDryRunTellsWhichParametersOfThePostAreCut() {
		final var permit = TestData.permitWithoutFiles("1");
		final var asset = permit.asset();
		final var candidate = permit.withAsset(new AssetData(asset.titleSuffix(), asset.description(), asset.issued(), asset.validTo(), Map.of("conditions", "x".repeat(300))));

		// A certificate the new system renders from the post would show the conditions cut
		assertThat(migrator.process(candidate, context(true)).detail()).isEqualTo("Parametrar som kortas till 255 tecken i PartyAssets: conditions");
	}

	@Test
	void anItemAlreadyMigratedIsLeftAsItIs() {
		final var candidate = TestData.permit("1");
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(item(ItemStatus.KLAR, ERRAND_ID)));

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
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(existing));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));

		final var result = migrator.process(candidate, context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.GRANSKAS);
		assertThat(result.rules()).containsExactly("G2", "G13");
		final var draftLeft = "Ärendet ALKT-2026-1 från en tidigare körning finns kvar i SupportManagement. Det slutförs om ärendet får migreras, annars hanteras det enligt avsnitt 10.3";
		assertThat(result.detail()).startsWith("G2 Tillståndets ägare skiljer sig från serveringsställets nuvarande ägare: AgarID 1, 2; " + draftLeft + "; G13");
		assertThat(result.errandNumber()).isEqualTo("ALKT-2026-1");
		assertThat(existing.getStatus()).isEqualTo(ItemStatus.GRANSKAS);
		assertThat(existing.getReason()).isEqualTo("G2 Tillståndets ägare skiljer sig från serveringsställets nuvarande ägare: AgarID 1, 2; " + draftLeft);
		assertThat(existing.getErrandId()).isEqualTo(ERRAND_ID);
		// Only read, to tell a draft from an errand an earlier run activated
		verify(supportManagement).getErrand(MUNICIPALITY_ID, ERRAND_ID);
		verifyNoMoreInteractions(supportManagement);
		verifyNoInteractions(partyAssets, relations);
	}

	@Test
	void anItemWhoseErrandAnEarlierRunActivatedIsMigratedAlthoughItWouldStopNow() {
		final var candidate = TestData.permit("1", RuleHit.of(OWNER_DIFFERS, "AgarID 1, 2"));
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(existing));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.ACTIVE)));

		final var result = migrator.process(candidate, context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.REDAN_MIGRERAD);
		assertThat(result.detail()).startsWith("Ärendet ALKT-2026-1 är redan aktivt i SupportManagement och räknas som migrerat. Ändringar i källan efter det förs inte över (beslut 11)");
		assertThat(existing.getStatus()).isEqualTo(ItemStatus.KLAR);
		verify(supportManagement, never()).updateErrand(anyString(), anyString(), any());
		verifyNoInteractions(partyAssets, relations);
	}

	@Test
	void anItemThatStopsTellsWhatAnEarlierRunLeftInTheTargetSystems() {
		final var candidate = TestData.permit("1", RuleHit.of(SALES_PLACE_CEASED));
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		existing.setErrandNumber(null);
		existing.setPartyAssetsId("asset-internal-id");
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(existing));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));

		final var result = migrator.process(candidate, context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAS_INTE);
		assertThat(existing.getStatus()).isEqualTo(ItemStatus.MIGRERAS_INTE);
		assertThat(existing.getReason()).endsWith("; Ärendet " + ERRAND_ID
			+ " från en tidigare körning finns kvar i SupportManagement, och tillståndet i PartyAssets. Det slutförs om ärendet får migreras, annars hanteras det enligt avsnitt 10.3");
		verify(supportManagement).getErrand(MUNICIPALITY_ID, ERRAND_ID);
		verifyNoMoreInteractions(supportManagement);
		verifyNoInteractions(partyAssets, relations);
	}

	@Test
	void anEndedPermitIsNotMigratedUnlessTheExceptionListSaysSo() {
		final var candidate = TestData.permit("1", RuleHit.of(SALES_PLACE_CEASED));

		final var notMigrated = migrator.process(candidate, context(true));
		assertThat(notMigrated.outcome()).isEqualTo(Outcome.MIGRERAS_INTE);

		final var exception = new ReviewExceptionEntity();
		exception.setOutcome(ExceptionOutcome.MIGRERA);
		when(exceptionRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(exception));

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
	void anOwningCompanyIsLookedUpAndAPersonWithoutANumberThatCanBeUsedIsCounted() {
		final var company = new LegalId("5599887766", PartyType.ENTERPRISE);
		when(party.getPartyId(MUNICIPALITY_ID, company)).thenReturn(Optional.of("company-party-id"));
		final var candidate = TestData.permitWithPersons("1",
			new Person("Ägarbolaget AB", company, false, List.of("Ägarbolag")),
			new Person("Utan Nummer", null, false, List.of()),
			// The source marks the person as foreign, so the missing number is expected and not counted
			new Person("Utländsk Person", null, true, List.of()));

		final var result = migrator.process(candidate, context(true));

		assertThat(result.outcome()).isEqualTo(Outcome.SKULLE_MIGRERAS);
		assertThat(result.detail()).isEqualTo("1 person(er) med betydande inflytande saknar nummer eller har ett nummer med fel format och läggs in utan partyId");
		verify(party).getPartyId(MUNICIPALITY_ID, company);
	}

	@Test
	void aPersonWhoseLookupFailsIsReviewedAndTheExceptionListLetsTheItemThrough() {
		when(party.getPartyId(MUNICIPALITY_ID, TestData.PERSON_ID)).thenThrow(new MigrationException("Uppslaget i Party misslyckades (502 BAD_GATEWAY)"));

		final var reviewed = migrator.process(TestData.permitWithoutFiles("1"), context(false));

		assertThat(reviewed.outcome()).isEqualTo(Outcome.GRANSKAS);
		assertThat(reviewed.rules()).containsExactly("G30");
		assertThat(reviewed.detail()).contains("G30 En person med betydande inflytande gick inte att slå upp i Party", "1 person(er), Uppslaget i Party misslyckades (502 BAD_GATEWAY)");

		final var exception = new ReviewExceptionEntity();
		exception.setOutcome(ExceptionOutcome.MIGRERA);
		when(exceptionRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(exception));

		assertThat(migrator.process(TestData.permitWithoutFiles("1"), context(true)).outcome()).isEqualTo(Outcome.SKULLE_MIGRERAS);
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
	void thePostGetsTheLegalBasisTheFirstDocumentOfTheHolderStates() throws IOException {
		Files.writeString(documents.resolve("delad.rtf"), "{\\rtf1 8 kap. 6 \\'a7 alkohollagen (2010:1622)}");
		Files.writeString(documents.resolve("beslut.rtf"), "{\\rtf1 Beslut}");
		Files.writeString(documents.resolve("bevis.rtf"), "{\\rtf1 Tillst\\'e5nd enligt 8 kap. 2 \\'a7 alkohollagen (2010:1622)}");
		Files.writeString(documents.resolve("senare.rtf"), "{\\rtf1 9 kap. 17 \\'a7 alkohollagen (2010:1622)}");
		final var candidate = TestData.permitWithLegalBasisDocuments("1",
			// A document another place also refers to may be that place's
			new FileReference("delad.rtf", DOCUMENTS, true),
			new FileReference("saknas.rtf", DOCUMENTS),
			new FileReference("beslut.rtf", DOCUMENTS),
			new FileReference("bevis.rtf", DOCUMENTS),
			new FileReference("senare.rtf", DOCUMENTS));
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of());
		when(supportManagement.createErrand(eq(MUNICIPALITY_ID), any())).thenReturn(ERRAND_ID);
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.getAttachments(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of());
		when(supportManagement.createAttachment(eq(MUNICIPALITY_ID), eq(ERRAND_ID), anyString(), any(), any())).thenReturn(Optional.of("attachment-id"));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.empty());
		when(partyAssets.createAsset(eq(MUNICIPALITY_ID), anyString(), assetCaptor.capture())).thenReturn(Optional.of("asset-internal-id"));

		final var dryRun = migrator.process(candidate, context(true));
		final var result = migrator.process(candidate, context(false));

		assertThat(dryRun.detail()).contains("Lagrum: 1 ur tillståndets beslut eller bevis");
		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).contains("Lagrum: 1 ur tillståndets beslut eller bevis");
		assertThat(assetCaptor.getValue().getAdditionalParameters()).containsEntry("legalBasis", "8 kap. 2 § alkohollagen (2010:1622)");
	}

	@Test
	void aPostWhoseDocumentsStateNoLegalBasisGetsNone() throws IOException {
		Files.writeString(documents.resolve("beslut.rtf"), "{\\rtf1 Beslut}");
		Files.writeString(documents.resolve("skannad.pdf"), "%PDF-1.4 8 kap. 2 § alkohollagen (2010:1622)");
		final var candidate = TestData.permitWithLegalBasisDocuments("1", new FileReference("beslut.rtf", DOCUMENTS), new FileReference("skannad.pdf", DOCUMENTS));

		final var result = migrator.process(candidate, context(true));

		assertThat(result.outcome()).isEqualTo(Outcome.SKULLE_MIGRERAS);
		assertThat(result.detail()).contains("Lagrum: står inte i tillståndets beslut eller bevis, så tillståndet får inget");
		// A permit without such documents is not told about
		assertThat(migrator.process(TestData.permit("2"), context(true)).detail()).doesNotContain("Lagrum");
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
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(existing));

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
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(existing));
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
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(item(ItemStatus.ARENDE_SKAPAT, "gone")));
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
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(gone));
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
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.empty()).thenReturn(Optional.of(assetAsCreated("found-after-409", Status.EXPIRED)));
		when(partyAssets.createAsset(eq(MUNICIPALITY_ID), anyString(), any())).thenReturn(Optional.empty());
		when(partyAssets.getAsset(MUNICIPALITY_ID, "found-after-409")).thenReturn(Optional.of(assetAsCreated("found-after-409", Status.ACTIVE)));

		final var found = migrator.process(TestData.permitWithoutFiles("1"), context(false));
		assertThat(found.outcome()).isEqualTo(Outcome.MIGRERAD);
		// The item has no exception now, so the permit an earlier run made EXPIRED is to be ACTIVE
		verify(partyAssets).updateAsset(MUNICIPALITY_ID, "found-after-409", new AssetUpdateRequest().status(Status.ACTIVE));
		assertThat(found.detail()).isEqualTo("Tillståndet i PartyAssets hade status EXPIRED från en tidigare körning och fick status ACTIVE");

		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.empty());
		final var notFound = migrator.process(TestData.permitWithoutFiles("1"), context(false));
		assertThat(notFound.outcome()).isEqualTo(Outcome.FEL);
		assertThat(notFound.detail()).isEqualTo("Steg Tillstånd i PartyAssets: PartyAssets svarade att tillståndet redan finns, men det gick inte att hitta");
	}

	@Test
	void thePostOfAnActiveErrandIsFoundButNeverChanged() {
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.ACTIVE)));
		// The new system has ended the permit since an earlier run migrated it, whose status row is gone
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.of(assetAsCreated("migrated-before", Status.EXPIRED)), Optional.empty());

		final var found = migrator.process(TestData.permit("1"), context(false));
		assertThat(found.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(found.detail()).isEqualTo("Tillståndet i PartyAssets från en tidigare körning ändras inte, eftersom ärendet redan är aktivt");
		verify(partyAssets, never()).updateAsset(anyString(), anyString(), any());
		verify(partyAssets, never()).createAsset(anyString(), anyString(), any());
		verify(partyAssets, never()).getAttachments(anyString(), anyString());

		final var notFound = migrator.process(TestData.permit("1"), context(false));
		assertThat(notFound.outcome()).isEqualTo(Outcome.FEL);
		assertThat(notFound.detail()).isEqualTo("Steg Tillstånd i PartyAssets: Ärendet är redan aktivt, men tillståndet finns inte i PartyAssets");
	}

	@Test
	void anExpiredPermitAccordingToTheExceptionList() {
		final var exception = new ReviewExceptionEntity();
		exception.setOutcome(ExceptionOutcome.MIGRERA_SOM_UTGANGET);
		when(exceptionRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(exception));
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.empty());
		when(partyAssets.createAsset(eq(MUNICIPALITY_ID), anyString(), assetCaptor.capture())).thenReturn(Optional.of("id"));
		when(partyAssets.getAsset(MUNICIPALITY_ID, "id")).thenReturn(Optional.of(assetAsCreated("id", Status.EXPIRED)));

		final var result = migrator.process(TestData.permitWithoutFiles("1", RuleHit.of(PERMIT_ENDED)), context(false));

		// Made ACTIVE, since an EXPIRED post takes no files, and EXPIRED last
		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(assetCaptor.getValue().getStatus()).isEqualTo(Status.ACTIVE);
		verify(partyAssets).updateAsset(MUNICIPALITY_ID, "id", new AssetUpdateRequest().status(Status.EXPIRED));
	}

	@Test
	void anExpiredPermitIsMadeActiveGetsItsFilesAndIsExpiredLast() throws IOException {
		Files.writeString(documents.resolve("beslut.rtf"), "dokument");
		expiredByTheExceptionList();
		when(partyAssets.createAsset(eq(MUNICIPALITY_ID), anyString(), assetCaptor.capture())).thenReturn(Optional.of("asset-internal-id"));
		when(partyAssets.getAttachments(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(List.of());
		when(partyAssets.createAttachment(MUNICIPALITY_ID, "asset-internal-id", "beslut.rtf", "dokument".getBytes(StandardCharsets.UTF_8), "Tillståndsbevis")).thenReturn(true);
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.EXPIRED)));

		final var result = migrator.process(TestData.permitWithCertificate("1", RuleHit.of(PERMIT_ENDED)), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(assetCaptor.getValue().getStatus()).isEqualTo(Status.ACTIVE);
		final var order = inOrder(partyAssets);
		order.verify(partyAssets).createAsset(eq(MUNICIPALITY_ID), anyString(), any());
		order.verify(partyAssets).createAttachment(MUNICIPALITY_ID, "asset-internal-id", "beslut.rtf", "dokument".getBytes(StandardCharsets.UTF_8), "Tillståndsbevis");
		order.verify(partyAssets).updateAsset(MUNICIPALITY_ID, "asset-internal-id", new AssetUpdateRequest().status(Status.EXPIRED));
		verify(partyAssets, never()).updateAsset(MUNICIPALITY_ID, "asset-internal-id", new AssetUpdateRequest().status(Status.ACTIVE));
		assertThat(result.detail()).contains("Tillståndet i PartyAssets har 1 av 1 filer: Tillståndsbevis 1", EXPIRED_LAST);
	}

	@Test
	void anExpiredPermitThatHasItsContentAndFilesStaysExpired() throws IOException {
		Files.writeString(documents.resolve("beslut.rtf"), "dokument");
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		existing.setFailedStep(Step.AKTIVERA);
		existing.setPartyAssetsId("asset-internal-id");
		final var exception = new ReviewExceptionEntity();
		exception.setOutcome(ExceptionOutcome.MIGRERA_SOM_UTGANGET);
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(existing));
		when(exceptionRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(exception));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(supportManagement.getAttachments(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of(new ErrandAttachment().id("a1").fileName("beslut.rtf").fileSize("dokument".length())));
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.EXPIRED)));
		when(partyAssets.getAttachments(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(List.of(new AssetAttachment().fileName("BESLUT.rtf").fileSize("dokument".length())));

		final var result = migrator.process(TestData.permitWithCertificate("1", RuleHit.of(PERMIT_ENDED)), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		verify(partyAssets, never()).updateAsset(anyString(), anyString(), any());
		verify(partyAssets, never()).createAttachment(anyString(), anyString(), anyString(), any(), anyString());
		verify(partyAssets, never()).createAsset(anyString(), anyString(), any());
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
		assertThat(result.detail()).startsWith("Steg Tillstånd i PartyAssets: Tillståndet asset-internal-id i PartyAssets fick inte status EXPIRED")
			.contains("Tillståndet i PartyAssets kan vara ACTIVE, fast det ska vara EXPIRED, tills nästa körning");
		// The step tries the status once more before it fails
		verify(partyAssets, times(2)).updateAsset(MUNICIPALITY_ID, "asset-internal-id", new AssetUpdateRequest().status(Status.EXPIRED));
	}

	@Test
	void anExpiredPermitWhoseFilesFailIsExpiredAgainBeforeTheStepFails() throws IOException {
		Files.writeString(documents.resolve("beslut.rtf"), "dokument");
		expiredByTheExceptionList();
		when(partyAssets.createAsset(eq(MUNICIPALITY_ID), anyString(), any())).thenReturn(Optional.of("asset-internal-id"));
		when(partyAssets.getAttachments(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(List.of());
		when(partyAssets.createAttachment(eq(MUNICIPALITY_ID), eq("asset-internal-id"), anyString(), any(), anyString())).thenThrow(Problem.valueOf(BAD_GATEWAY, "Tjänsten svarar inte"));
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.EXPIRED)));

		final var result = migrator.process(TestData.permitWithCertificate("1", RuleHit.of(PERMIT_ENDED)), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).startsWith("Steg Tillstånd i PartyAssets: 502 BAD_GATEWAY Tjänsten svarar inte")
			.doesNotContain("fast det ska vara EXPIRED");
		final var order = inOrder(partyAssets);
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
			.contains("Tillståndet i PartyAssets kan vara ACTIVE, fast det ska vara EXPIRED, tills nästa körning");
		verify(partyAssets).updateAsset(MUNICIPALITY_ID, "asset-internal-id", new AssetUpdateRequest().status(Status.EXPIRED));
	}

	/**
	 * Permit 1 is to be migrated as expired, its errand is a draft and its post does not exist yet.
	 */
	private void expiredByTheExceptionList() {
		final var exception = new ReviewExceptionEntity();
		exception.setOutcome(ExceptionOutcome.MIGRERA_SOM_UTGANGET);
		when(exceptionRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(exception));
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.empty());
	}

	@Test
	void thePermitOfAnEarlierRunGetsTheStatusTheExceptionListGivesNow() {
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		existing.setFailedStep(Step.AKTIVERA);
		existing.setPartyAssetsId("asset-internal-id");
		final var exception = new ReviewExceptionEntity();
		exception.setOutcome(ExceptionOutcome.MIGRERA_SOM_UTGANGET);
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(existing));
		when(exceptionRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(exception));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(supportManagement.getAttachments(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of());
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id"))
			.thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.ACTIVE)))
			.thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.EXPIRED)));

		final var result = migrator.process(TestData.permit("1", RuleHit.of(PERMIT_ENDED)), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).contains(EXPIRED_LAST);
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
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(existing));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(partyAssets.getAsset(MUNICIPALITY_ID, "gone-asset-id")).thenReturn(Optional.empty());
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.empty());
		when(partyAssets.createAsset(eq(MUNICIPALITY_ID), anyString(), assetCaptor.capture())).thenReturn(Optional.of("new-asset-id"));

		final var result = migrator.process(TestData.permitWithoutFiles("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).contains("Tillståndet gone-asset-id fanns inte längre i PartyAssets");
		assertThat(assetCaptor.getValue().getStatus()).isEqualTo(Status.ACTIVE);
		assertThat(existing.getPartyAssetsId()).isEqualTo("new-asset-id");
	}

	@Test
	void aPermitWithAStatusTheMigrationNeverGivesIsLeftAsItIs() {
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.of(new Asset().id("blocked-asset-id").status(Status.BLOCKED)));

		final var result = migrator.process(TestData.permitWithoutFiles("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).isEqualTo("Steg Tillstånd i PartyAssets: Tillståndet blocked-asset-id i PartyAssets har status BLOCKED, som migreringen aldrig ger, och ändras inte");
		verify(partyAssets, never()).updateAsset(anyString(), anyString(), any());
	}

	@Test
	void thePermitOfAnEarlierRunGetsTheContentTheSourceGivesNow() {
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		existing.setFailedStep(Step.AKTIVERA);
		existing.setPartyAssetsId("asset-internal-id");
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(existing));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id"))
			.thenReturn(Optional.of(staleAsset("asset-internal-id", Status.ACTIVE)))
			.thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.ACTIVE)));

		final var result = migrator.process(TestData.permitWithoutFiles("1"), context(false));

		final var wanted = new AssetMapper(properties).toAsset(TestData.permitWithoutFiles("1"), MUNICIPALITY_ID, ERRAND_ID, PARTY_ID, false);
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
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(existing));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		// PartyAssets before 6.8 answers the change with 204 and keeps the content
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(Optional.of(staleAsset("asset-internal-id", Status.ACTIVE)));

		final var result = migrator.process(TestData.permitWithoutFiles("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).isEqualTo("Steg Tillstånd i PartyAssets: PartyAssets gjorde inte hela ändringen av tillståndet asset-internal-id, vilket kräver PartyAssets 6.8 eller senare");
		assertThat(existing.getStatus()).isEqualTo(ItemStatus.FEL);
		assertThat(existing.getFailedStep()).isEqualTo(Step.TILLSTAND);
	}

	@Test
	void aPostAnEarlierRunMadeForAnotherHolderIsFoundThroughItsRelationAndLeftAsItIs() {
		// The earlier run made the post but lost the answer, and the holder has changed in the source since
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		existing.setFailedStep(Step.TILLSTAND);
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(existing));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.empty());
		when(relations.findRelationsFrom(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of(
			new Relation().target(new ResourceIdentifier().resourceId(PERMIT_ERRAND_ID).service("supportmanagement")),
			new Relation().target(new ResourceIdentifier().resourceId("asset-internal-id").service("partyassets"))));
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(Optional.of(staleAsset("asset-internal-id", Status.ACTIVE).partyId("former-holder-id")));

		final var result = migrator.process(TestData.permitWithoutFiles("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).isEqualTo("Steg Tillstånd i PartyAssets: Tillståndet asset-internal-id i PartyAssets tillhör en annan part än den källan anger nu och ändras inte (avsnitt 10.3)");
		verify(partyAssets, never()).createAsset(anyString(), anyString(), any());
		verify(partyAssets, never()).getAsset(MUNICIPALITY_ID, PERMIT_ERRAND_ID);
	}

	@Test
	void aStatusTableThatCannotBeReadFailsTheItemOnly() {
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenThrow(new IllegalStateException("Låst"));

		final var result = migrator.process(TestData.permit("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).isEqualTo("Steg Läs och bedöm: statustabellen gick inte att läsa: IllegalStateException: Låst");
		verifyNoInteractions(supportManagement, partyAssets, relations);
	}

	@Test
	void aPostOfAnotherPartyIsLeftAsItIs() {
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		existing.setFailedStep(Step.AKTIVERA);
		existing.setPartyAssetsId("asset-internal-id");
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(existing));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		// The holder's number was corrected in the source after the earlier run made the post
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(Optional.of(staleAsset("asset-internal-id", Status.ACTIVE).partyId("another-party-id")));

		final var result = migrator.process(TestData.permitWithoutFiles("1"), context(false));

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
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(existing));
		when(exceptionRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(exception));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id"))
			.thenReturn(Optional.of(staleAsset("asset-internal-id", Status.ACTIVE)))
			.thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.ACTIVE)))
			.thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.EXPIRED)));

		final var result = migrator.process(TestData.permitWithoutFiles("1", RuleHit.of(PERMIT_ENDED)), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).contains("Tillståndet i PartyAssets från en tidigare körning fick titel, giltighet och parametrar som källan ger nu", EXPIRED_LAST);
		// An EXPIRED post can no longer get the content
		final var order = inOrder(partyAssets);
		order.verify(partyAssets).updateAsset(eq(MUNICIPALITY_ID), eq("asset-internal-id"), updateCaptor.capture());
		assertThat(updateCaptor.getValue().getStatus()).isNull();
		assertThat(updateCaptor.getValue().getTitle()).isNotNull();
		order.verify(partyAssets).getAsset(MUNICIPALITY_ID, "asset-internal-id");
		order.verify(partyAssets).updateAsset(MUNICIPALITY_ID, "asset-internal-id", new AssetUpdateRequest().status(Status.EXPIRED));
	}

	@Test
	void aContentChangeThatPartyAssetsIgnoresFailsTheStepAndThePostIsExpiredAgain() {
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		existing.setFailedStep(Step.AKTIVERA);
		existing.setPartyAssetsId("asset-internal-id");
		final var exception = new ReviewExceptionEntity();
		exception.setOutcome(ExceptionOutcome.MIGRERA_SOM_UTGANGET);
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(existing));
		when(exceptionRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(exception));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		// PartyAssets before 6.8 answers the change with 204 and keeps the content; the post that is to be EXPIRED then gets
		// that status, so that it does not stand as valid
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id"))
			.thenReturn(Optional.of(staleAsset("asset-internal-id", Status.ACTIVE)), Optional.of(staleAsset("asset-internal-id", Status.ACTIVE)))
			.thenReturn(Optional.of(staleAsset("asset-internal-id", Status.EXPIRED)));

		final var result = migrator.process(TestData.permitWithoutFiles("1", RuleHit.of(PERMIT_ENDED)), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).isEqualTo("Steg Tillstånd i PartyAssets: PartyAssets gjorde inte hela ändringen av tillståndet asset-internal-id, vilket kräver PartyAssets 6.8 eller senare; "
			+ "Undantagslistan: MIGRERA_SOM_UTGANGET; Tillståndet i PartyAssets fick status EXPIRED när steget misslyckades");
		verify(partyAssets).updateAsset(MUNICIPALITY_ID, "asset-internal-id", new AssetUpdateRequest().status(Status.EXPIRED));
	}

	@Test
	void aPostGoneRightAfterItsChangeFailsTheStep() {
		final var existing = item(ItemStatus.FEL, ERRAND_ID);
		existing.setFailedStep(Step.AKTIVERA);
		existing.setPartyAssetsId("asset-internal-id");
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(existing));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(partyAssets.getAsset(MUNICIPALITY_ID, "asset-internal-id")).thenReturn(Optional.of(staleAsset("asset-internal-id", Status.ACTIVE))).thenReturn(Optional.empty());

		final var result = migrator.process(TestData.permitWithoutFiles("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).isEqualTo("Steg Tillstånd i PartyAssets: Tillståndet asset-internal-id fanns inte i PartyAssets efter ändringen");
	}

	@Test
	void anExpiredPermitThatIsToBeActiveGetsItsStatusBeforeItsContent() {
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.of(staleAsset("stale-asset-id", Status.EXPIRED)));
		when(partyAssets.getAsset(MUNICIPALITY_ID, "stale-asset-id")).thenReturn(Optional.of(assetAsCreated("stale-asset-id", Status.ACTIVE)));

		final var result = migrator.process(TestData.permitWithoutFiles("1"), context(false));

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
	void anExpiredPermitThatStaysExpiredGetsItsContentWhileActive() {
		final var exception = new ReviewExceptionEntity();
		exception.setOutcome(ExceptionOutcome.MIGRERA_SOM_UTGANGET);
		when(exceptionRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(exception));
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.of(staleAsset("stale-asset-id", Status.EXPIRED)));
		when(partyAssets.getAsset(MUNICIPALITY_ID, "stale-asset-id"))
			.thenReturn(Optional.of(assetAsCreated("stale-asset-id", Status.ACTIVE)))
			.thenReturn(Optional.of(assetAsCreated("stale-asset-id", Status.EXPIRED)));

		final var result = migrator.process(TestData.permitWithoutFiles("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).contains("Tillståndet i PartyAssets från en tidigare körning fick titel, giltighet och parametrar som källan ger nu", EXPIRED_LAST);
		final var order = inOrder(partyAssets);
		order.verify(partyAssets).updateAsset(MUNICIPALITY_ID, "stale-asset-id", new AssetUpdateRequest().status(Status.ACTIVE));
		order.verify(partyAssets).updateAsset(eq(MUNICIPALITY_ID), eq("stale-asset-id"), updateCaptor.capture());
		order.verify(partyAssets).updateAsset(MUNICIPALITY_ID, "stale-asset-id", new AssetUpdateRequest().status(Status.EXPIRED));
	}

	@Test
	void anExpiredPermitWhoseContentFailsIsExpiredAgainBeforeTheStepFails() {
		final var exception = new ReviewExceptionEntity();
		exception.setOutcome(ExceptionOutcome.MIGRERA_SOM_UTGANGET);
		when(exceptionRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(exception));
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.of(staleAsset("stale-asset-id", Status.EXPIRED)));
		// The content is refused while the post is ACTIVE, after which the post is EXPIRED again
		lenient().doThrow(Problem.valueOf(BAD_GATEWAY, "Tjänsten svarar inte")).when(partyAssets)
			.updateAsset(eq(MUNICIPALITY_ID), eq("stale-asset-id"), argThat(update -> update.getStatus() == null));
		when(partyAssets.getAsset(MUNICIPALITY_ID, "stale-asset-id")).thenReturn(Optional.of(assetAsCreated("stale-asset-id", Status.EXPIRED)));

		final var result = migrator.process(TestData.permitWithoutFiles("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).startsWith("Steg Tillstånd i PartyAssets: 502 BAD_GATEWAY Tjänsten svarar inte")
			.contains("Tillståndet i PartyAssets fick status EXPIRED när steget misslyckades");
		final var order = inOrder(partyAssets);
		order.verify(partyAssets).updateAsset(MUNICIPALITY_ID, "stale-asset-id", new AssetUpdateRequest().status(Status.ACTIVE));
		order.verify(partyAssets).updateAsset(eq(MUNICIPALITY_ID), eq("stale-asset-id"), argThat(update -> update.getStatus() == null));
		order.verify(partyAssets).updateAsset(MUNICIPALITY_ID, "stale-asset-id", new AssetUpdateRequest().status(Status.EXPIRED));
	}

	@Test
	void whatPartyAssetsCannotChangeIsReported() {
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn(errand(LifecycleEnum.DRAFT));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID))
			.thenReturn(Optional.of(assetAsCreated("asset-internal-id", Status.ACTIVE).type("OldPermitType").issued(LocalDate.of(2018, 1, 1))));

		final var result = migrator.process(TestData.permitWithoutFiles("1"), context(false));

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
		final var context = new RunContext("run-id", MUNICIPALITY_ID, false, new Preflight.Result(List.of(), Map.of(MUNICIPALITY_ID, TestData.labelIds()), Map.of(MUNICIPALITY_ID, TestData.purposes())), 0,
			new AtomicBoolean(), this::fileArea);

		final var result = migrator.process(TestData.permit("1"), context);

		assertThat(result.outcome()).isEqualTo(Outcome.EJ_KORD);
		verify(itemRepository, never()).save(any());
		verifyNoInteractions(supportManagement);
	}

	@Test
	void anInspectionErrandWaitsForThePermitOfItsPlace() {
		final var permitKey = new ItemKey(Source.ALKT, TILLSTAND, "1");
		lenient().when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(item(ItemStatus.GRANSKAS, null)));

		final var result = migrator.process(TestData.inspection("700", permitKey, TestData.HOLDER_ID), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.VANTAR);
		assertThat(result.detail()).isEqualTo("Väntar på tillståndet ALKT/TILLSTAND/1, som är GRANSKAS");
		assertThat(savedStatuses).containsExactly(ItemStatus.VANTAR);
	}

	@Test
	void anInspectionErrandThatCanGetNoRelationDoesNotWaitForThePermit() {
		final var permitKey = new ItemKey(Source.ALKT, TILLSTAND, "1");
		lenient().when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(item(ItemStatus.GRANSKAS, null)));

		final var formerHolder = new LegalId("5599887766", PartyType.ENTERPRISE);
		lenient().when(party.getPartyId(MUNICIPALITY_ID, formerHolder)).thenReturn(Optional.of("former-party"));

		final var beforeIssue = migrator.process(TestData.inspection("703", new PermitLink(new PlacePermit(permitKey, LocalDate.of(2021, 1, 1), null, true), TestData.HOLDER_ID, null,
			LocalDate.of(2020, 1, 1))), context(true));
		final var unknownPermitHolder = migrator.process(TestData.inspection("704", permitKey, null, TestData.HOLDER_ID), context(true));
		final var former = migrator.process(TestData.inspection("705", permitKey, formerHolder), context(true));

		assertThat(beforeIssue.outcome()).isEqualTo(Outcome.SKULLE_MIGRERAS);
		assertThat(beforeIssue.detail()).contains("Ingen relation: tillsynen gjordes före att tillståndet utfärdades 2021-01-01, och det går inte att se vilken tillståndshavare den gällde")
			.doesNotContain("Väntar");
		assertThat(unknownPermitHolder.outcome()).isEqualTo(Outcome.SKULLE_MIGRERAS);
		assertThat(unknownPermitHolder.detail()).contains("Ingen relation: det går inte att se vem som har tillståndet").doesNotContain("Väntar");
		assertThat(former.outcome()).isEqualTo(Outcome.SKULLE_MIGRERAS);
		assertThat(former.detail()).contains("Ingen relation: ärendet gällde en annan tillståndshavare än den som har tillståndet").doesNotContain("Väntar");
		// Without a known holder, an inspection after the permit was issued concerned its holder, so it waits for the permit
		// (decision 61)
		assertThat(migrator.process(TestData.inspection("706", permitKey, null), context(true)).outcome()).isEqualTo(Outcome.VANTAR);
	}

	@Test
	void anInspectionErrandGetsItsRelation() {
		final var permitKey = new ItemKey(Source.ALKT, TILLSTAND, "1");
		lenient().when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(item(ItemStatus.KLAR, PERMIT_ERRAND_ID)));
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
		lenient().when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(item(ItemStatus.KLAR, PERMIT_ERRAND_ID)));
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
		lenient().when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(item(ItemStatus.KLAR, PERMIT_ERRAND_ID)));
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

		lenient().when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(item(ItemStatus.MIGRERAS_INTE, null)));
		assertThat(migrator.process(TestData.inspection("701", permitKey, TestData.HOLDER_ID), context(true)).detail()).isEqualTo("Ingen relation: tillståndet migreras inte");

		lenient().when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(item(ItemStatus.KLAR, PERMIT_ERRAND_ID)));
		final var former = migrator.process(TestData.inspection("702", permitKey, formerHolder), context(true));
		assertThat(former.outcome()).isEqualTo(Outcome.SKULLE_MIGRERAS);
		assertThat(former.rules()).containsExactly("G10");
		assertThat(former.detail()).contains("G10 Tillståndshavaren som ärendet gällde finns inte i Party", "Ingen relation: ärendet gällde en annan tillståndshavare än den som har tillståndet");
		// Without a known holder, the day of the inspection relates it
		assertThat(migrator.process(TestData.inspection("703", permitKey, null), context(true)).detail())
			.contains("Relation till tillståndsärendet ALKT/TILLSTAND/1, tillsynen gjordes efter att tillståndet utfärdades 2019-05-01");
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
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSYN, "700"))).thenReturn(Optional.of(inspection));
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(item(ItemStatus.KLAR, PERMIT_ERRAND_ID)));
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
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSYN, "700"))).thenReturn(Optional.of(item(ItemStatus.KLAR, ERRAND_ID)));
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(item(ItemStatus.MIGRERAS_INTE, null)));

		assertThat(migrator.process(TestData.inspection("700", permitKey, TestData.HOLDER_ID), context(false)).detail()).isNull();
		assertThat(migrator.process(TestData.inspection("700", permitKey, TestData.HOLDER_ID), context(true)).outcome()).isEqualTo(Outcome.REDAN_MIGRERAD);
		verifyNoInteractions(relations);
		verify(itemRepository, never()).save(any());
	}

	@Test
	void aRelationAddedAfterwardsThatFailsIsReportedAndTheItemStaysMigrated() {
		final var permitKey = new ItemKey(Source.ALKT, TILLSTAND, "1");
		final var inspection = item(ItemStatus.KLAR, ERRAND_ID);
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSYN, "700"))).thenReturn(Optional.of(inspection));
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(item(ItemStatus.KLAR, PERMIT_ERRAND_ID)));
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

	@Test
	void aPermitGetsTheDecisionItWasGrantedByBeforeItsErrandIsActivated() throws IOException {
		withDecisions();
		stubNewPermit();
		when(supportManagement.getInvestigations(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of(new Investigation().id("investigation-1")));
		when(supportManagement.getDecisions(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of());
		when(supportManagement.createDecision(eq(MUNICIPALITY_ID), eq(ERRAND_ID), decisionCaptor.capture())).thenReturn("decision-1");

		final var result = migrator.process(TestData.permitWithDecision("1"), contextWithOutcomes(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).isEqualTo("Beslut: Bifall med villkor, fattat 2019-04-30, 1 villkor, 1 dokument");
		assertThat(savedStatuses).containsExactly(ItemStatus.TILLSTANDSHAVARE_KLAR, ItemStatus.ARENDE_SKAPAT, ItemStatus.BILAGOR_KLARA, ItemStatus.TILLSTAND_SKAPAT,
			ItemStatus.BESLUT_KLART, ItemStatus.KLAR);
		final var draft = decisionCaptor.getValue();
		assertThat(draft.getStatus()).isEqualTo("DRAFT");
		assertThat(draft.getType()).isEqualTo("SERVERINGSTILLSTAND");
		assertThat(draft.getOutcome()).isEqualTo("APPROVAL_WITH_CONDITIONS");
		assertThat(draft.getDecidedBy()).isEqualTo("Anna Berg");
		assertThat(draft.getInvestigationId()).isEqualTo("investigation-1");
		assertThat(draft.getParameters()).extracting(generated.se.sundsvall.supportmanagement.Parameter::getKey).contains("decisionText");
		// As Draken writes a decision: the draft, its terms and its document, and the completion, all before the errand is
		// activated, while no process or notice hears of it
		final var order = inOrder(supportManagement, partyAssets);
		order.verify(partyAssets).createAsset(eq(MUNICIPALITY_ID), anyString(), any());
		order.verify(supportManagement).createDecision(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any());
		order.verify(supportManagement).createDecisionTerm(MUNICIPALITY_ID, ERRAND_ID, "decision-1", new DecisionTerm().sortOrder(1).text("Mat ska serveras"));
		order.verify(supportManagement).linkDecisionAttachment(MUNICIPALITY_ID, ERRAND_ID, "decision-1", "attachment-beslut");
		order.verify(supportManagement).updateDecision(MUNICIPALITY_ID, ERRAND_ID, "decision-1",
			new Decision().status("COMPLETED").completedAt(OffsetDateTime.parse("2019-04-30T09:00+02:00")));
		order.verify(supportManagement).updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), eq(new Errand().lifecycle(LifecycleEnum.ACTIVE)));
		// The application is no document of the decision
		verify(supportManagement, never()).linkDecisionAttachment(MUNICIPALITY_ID, ERRAND_ID, "decision-1", "attachment-id");
	}

	@Test
	void aDecisionAnEarlierRunCompletedAsTheSourceGivesItIsKept() throws IOException {
		withDecisions();
		stubNewPermit();
		final var candidate = TestData.permitWithDecision("1");
		final var mapper = new TargetMapper(properties, CLOCK);
		// As SupportManagement gives it back: with ids, its times in UTC and its account in capitals
		final var found = mapper.toDecisionDraft(candidate, null, null, "Bifall med villkor")
			.id("decision-1")
			.status("COMPLETED")
			.decidedAt(OffsetDateTime.parse("2019-04-30T07:00Z"))
			.completedAt(OffsetDateTime.parse("2019-04-30T07:00Z"))
			.createdBy("AB12CDE")
			.terms(List.of(new DecisionTerm().id("term-1").sortOrder(1).text("Mat ska serveras")))
			.attachments(List.of(new ErrandAttachment().id("attachment-beslut")));
		when(supportManagement.getDecisions(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of(found));

		final var result = migrator.process(candidate, contextWithOutcomes(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).isEqualTo("Beslut: Bifall med villkor, fattat 2019-04-30, 1 villkor, 1 dokument; "
			+ "Beslutet från en tidigare körning är redan fattat i SupportManagement och har det källan ger nu");
		assertThat(savedStatuses).contains(ItemStatus.BESLUT_KLART);
		verify(supportManagement, never()).deleteDecision(anyString(), anyString(), anyString());
		verify(supportManagement, never()).createDecision(anyString(), anyString(), any());
		verify(supportManagement, never()).updateDecision(anyString(), anyString(), anyString(), any());
		verify(supportManagement, never()).linkDecisionAttachment(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void aDecisionAnEarlierRunWroteThatIsNotAsTheSourceGivesItIsWrittenAgain() throws IOException {
		withDecisions();
		stubNewPermit();
		final var candidate = TestData.permitWithDecision("1");
		final var mapper = new TargetMapper(properties, CLOCK);
		// Completed with a last day the source no longer gives, which a change could not empty
		final var completed = mapper.toDecisionDraft(candidate, null, null, "Bifall med villkor")
			.id("decision-1")
			.status("COMPLETED")
			.validTo(LocalDate.of(2020, 12, 31))
			.completedAt(OffsetDateTime.parse("2019-04-30T09:00+02:00"))
			.createdBy("ab12cde")
			.terms(List.of(new DecisionTerm().id("term-1").sortOrder(1).text("Mat ska serveras")))
			.attachments(List.of(new ErrandAttachment().id("attachment-beslut"), new ErrandAttachment().id("attachment-old")));
		// And a draft that a run left when it stopped
		final var draft = new Decision().id("decision-2").status("DRAFT").createdBy("ab12cde");
		when(supportManagement.getDecisions(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of(completed, draft));
		when(supportManagement.createDecision(eq(MUNICIPALITY_ID), eq(ERRAND_ID), decisionCaptor.capture())).thenReturn("decision-3");

		final var result = migrator.process(candidate, contextWithOutcomes(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).isEqualTo("Beslut: Bifall med villkor, fattat 2019-04-30, 1 villkor, 1 dokument; "
			+ "Beslutet från en tidigare körning togs bort och skrevs på nytt med det källan ger nu");
		final var order = inOrder(supportManagement);
		order.verify(supportManagement).deleteDecision(MUNICIPALITY_ID, ERRAND_ID, "decision-1");
		order.verify(supportManagement).deleteDecision(MUNICIPALITY_ID, ERRAND_ID, "decision-2");
		order.verify(supportManagement).createDecision(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any());
		order.verify(supportManagement).createDecisionTerm(MUNICIPALITY_ID, ERRAND_ID, "decision-3", new DecisionTerm().sortOrder(1).text("Mat ska serveras"));
		order.verify(supportManagement).linkDecisionAttachment(MUNICIPALITY_ID, ERRAND_ID, "decision-3", "attachment-beslut");
		order.verify(supportManagement).updateDecision(MUNICIPALITY_ID, ERRAND_ID, "decision-3",
			new Decision().status("COMPLETED").completedAt(OffsetDateTime.parse("2019-04-30T09:00+02:00")));
		assertThat(decisionCaptor.getValue().getValidTo()).isNull();
		verify(supportManagement, never()).linkDecisionAttachment(MUNICIPALITY_ID, ERRAND_ID, "decision-3", "attachment-old");
	}

	@Test
	void aDecisionSomeoneElseWroteStopsTheItemBeforeItsErrandIsActivated() throws IOException {
		withDecisions();
		stubNewPermit();
		when(supportManagement.getDecisions(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of(
			new Decision().id("decision-1").status("DRAFT").createdBy("ab12cde"),
			new Decision().id("decision-2").status("COMPLETED").createdBy("xy98zzz")));

		final var result = migrator.process(TestData.permitWithDecision("1"), contextWithOutcomes(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).isEqualTo("Steg Beslut: Ärendet har redan ett beslut som någon annan än migreringen har skrivit, och det ändras inte");
		verify(supportManagement, never()).updateErrand(eq(MUNICIPALITY_ID), eq(ERRAND_ID), eq(new Errand().lifecycle(LifecycleEnum.ACTIVE)));
		verify(supportManagement, never()).deleteDecision(anyString(), anyString(), anyString());
		verify(supportManagement, never()).createDecision(anyString(), anyString(), any());
	}

	@Test
	void aDraftARunLeftWhenItStoppedIsWrittenAgain() throws IOException {
		withDecisions();
		stubNewPermit();
		when(supportManagement.getDecisions(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of(new Decision().id("decision-1").status("DRAFT").createdBy("ab12cde")
			.terms(List.of(new DecisionTerm().id("term-1").sortOrder(1).text("Mat ska serveras")))));
		when(supportManagement.createDecision(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any())).thenReturn("decision-2");

		final var result = migrator.process(TestData.permitWithDecision("1"), contextWithOutcomes(false));

		assertThat(result.detail()).endsWith("; Beslutet från en tidigare körning togs bort och skrevs på nytt med det källan ger nu");
		final var order = inOrder(supportManagement);
		order.verify(supportManagement).deleteDecision(MUNICIPALITY_ID, ERRAND_ID, "decision-1");
		order.verify(supportManagement).createDecision(eq(MUNICIPALITY_ID), eq(ERRAND_ID), any());
		order.verify(supportManagement).createDecisionTerm(MUNICIPALITY_ID, ERRAND_ID, "decision-2", new DecisionTerm().sortOrder(1).text("Mat ska serveras"));
		order.verify(supportManagement).linkDecisionAttachment(MUNICIPALITY_ID, ERRAND_ID, "decision-2", "attachment-beslut");
		order.verify(supportManagement).updateDecision(MUNICIPALITY_ID, ERRAND_ID, "decision-2",
			new Decision().status("COMPLETED").completedAt(OffsetDateTime.parse("2019-04-30T09:00+02:00")));
	}

	@Test
	void withoutAnAdAccountNoDecisionOnTheErrandIsTheMigrationsOwn() throws IOException {
		properties = TestData.propertiesWithDecisions(documents.toString(), attachments.toString(), " ");
		migrator = migrator(properties);
		stubNewPermit();
		when(supportManagement.getDecisions(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of(new Decision().id("decision-1").status("DRAFT").createdBy("ab12cde")));

		final var result = migrator.process(TestData.permitWithDecision("1"), contextWithOutcomes(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).startsWith("Steg Beslut: Ärendet har redan ett beslut som någon annan än migreringen har skrivit");
		verify(supportManagement, never()).deleteDecision(anyString(), anyString(), anyString());
	}

	@Test
	void anErrandAnEarlierRunActivatedGetsNoNewDecisionAndADryRunSaysSoToo() throws IOException {
		withDecisions();
		Files.writeString(documents.resolve("beslut.rtf"), "dokument");
		Files.writeString(attachments.resolve("ansokan.pdf"), "bilaga");
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of(errand(LifecycleEnum.ACTIVE)));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.of(assetAsCreated("migrated-before", Status.ACTIVE)));

		final var real = migrator.process(TestData.permitWithDecision("1"), contextWithOutcomes(false));

		assertThat(real.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(real.detail()).endsWith("; Ärendet får inget nytt beslut, eftersom det redan är aktivt (beslut 11)");
		assertThat(savedStatuses).contains(ItemStatus.BESLUT_KLART);
		verify(supportManagement, never()).getDecisions(anyString(), anyString());

		// A dry run of an item whose errand an earlier run activated, before the status table says so
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(item(ItemStatus.FEL, ERRAND_ID)));
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.ACTIVE)));

		final var dry = migrator.process(TestData.permitWithDecision("1"), contextWithOutcomes(true));

		assertThat(dry.outcome()).isEqualTo(Outcome.SKULLE_MIGRERAS);
		assertThat(dry.detail()).isEqualTo("Ärendet får inget nytt beslut, eftersom det redan är aktivt (beslut 11)");
	}

	@Test
	void anItemWhoseCleanupDidNotFinishIsNotMigrated() {
		final var item = item(ItemStatus.RENSAS, ERRAND_ID);
		when(itemRepository.findByKey(new ItemKey(Source.ALKT, TILLSTAND, "1"))).thenReturn(Optional.of(item));

		final var result = migrator.process(TestData.permit("1"), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.FEL);
		assertThat(result.detail()).isEqualTo("Rensningen av ärendet blev inte klar och görs klar innan ärendet migreras igen (avsnitt 10.3)");
		assertThat(result.errandNumber()).isEqualTo("ALKT-2026-1");
		assertThat(item.getStatus()).isEqualTo(ItemStatus.RENSAS);
		verify(itemRepository, never()).save(any());
		verifyNoInteractions(supportManagement, partyAssets, relations, party);
	}

	@Test
	void aDryRunTellsWhichDecisionTheErrandGetsOrThatItGetsNone() throws IOException {
		withDecisions();
		Files.writeString(documents.resolve("beslut.rtf"), "dokument");
		Files.writeString(attachments.resolve("ansokan.pdf"), "bilaga");

		assertThat(migrator.process(TestData.permitWithDecision("1"), contextWithOutcomes(true)).detail()).isEqualTo("Beslut: Bifall med villkor, fattat 2019-04-30, 1 villkor, 1 dokument");
		// Without the outcomes of the namespace the outcome stands by its name
		assertThat(migrator.process(TestData.permitWithDecision("1"), context(true)).detail()).isEqualTo("Beslut: APPROVAL_WITH_CONDITIONS, fattat 2019-04-30, 1 villkor, 1 dokument");
		assertThat(migrator.process(TestData.permitWithoutFiles("2"), context(true)).detail()).isEqualTo("Inget beslut: AlkT har inget beslut med tidpunkt för tillståndet");
		// A type without decisions says nothing of them
		assertThat(migrator(TestData.properties(documents.toString(), attachments.toString())).process(TestData.permitWithDecision("1"), context(true)).detail()).isNull();
		verifyNoInteractions(supportManagement);
	}

	@Test
	void aPermitWithoutADecisionIsMigratedWithoutOne() throws IOException {
		withDecisions();
		stubNewPermit();

		final var result = migrator.process(TestData.permit("1"), contextWithOutcomes(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).isEqualTo("Inget beslut: AlkT har inget beslut med tidpunkt för tillståndet");
		assertThat(savedStatuses).doesNotContain(ItemStatus.BESLUT_KLART);
		verify(supportManagement, never()).getDecisions(anyString(), anyString());
	}

	@Test
	void aDryRunCountsOnlyTheDocumentsOfTheDecisionThatWouldBeUploaded() throws IOException {
		withDecisions();
		// beslut.rtf, the document of the decision, is missing on the file area
		Files.writeString(attachments.resolve("ansokan.pdf"), "bilaga");

		final var result = migrator.process(TestData.permitWithDecision("1"), contextWithOutcomes(true));

		assertThat(result.detail()).isEqualTo("G14 1 fil saknas på filarean (beslut.rtf); Beslut: Bifall med villkor, fattat 2019-04-30, 1 villkor, 0 dokument");
	}

	@Test
	void aDocumentationErrandIsRelatedFromThePermitErrandItIsFiledUnder() {
		final var permitKey = new ItemKey(Source.ALKT, TILLSTAND, "1");
		lenient().when(itemRepository.findByKey(permitKey)).thenReturn(Optional.of(item(ItemStatus.KLAR, PERMIT_ERRAND_ID)));
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-DOKUMENTATION-31020")).thenReturn(List.of());
		when(supportManagement.createErrand(eq(MUNICIPALITY_ID), errandCaptor.capture())).thenReturn(ERRAND_ID);
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));
		when(relations.findRelations(MUNICIPALITY_ID, "LINK", PERMIT_ERRAND_ID, ERRAND_ID)).thenReturn(List.of());
		when(relations.createRelation(eq(MUNICIPALITY_ID), any())).thenReturn("relation-id");

		final var result = migrator.process(TestData.documentation("31020", permitKey), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.detail()).isEqualTo("Relation till tillståndsärendet ALKT/TILLSTAND/1");
		assertThat(savedStatuses).containsExactly(ItemStatus.TILLSTANDSHAVARE_KLAR, ItemStatus.ARENDE_SKAPAT, ItemStatus.BILAGOR_KLARA, ItemStatus.RELATION_KLAR, ItemStatus.KLAR);
		final var relation = ArgumentCaptor.forClass(Relation.class);
		verify(relations).createRelation(eq(MUNICIPALITY_ID), relation.capture());
		assertThat(relation.getValue().getSource().getResourceId()).isEqualTo(PERMIT_ERRAND_ID);
		assertThat(relation.getValue().getTarget().getResourceId()).isEqualTo(ERRAND_ID);
		// The holder it concerned, in the role of the inspections, so that it does not show on Mina sidor
		assertThat(errandCaptor.getValue().getStakeholders()).extracting("role").containsExactly("INSPECTED");
		assertThat(errandCaptor.getValue().getLabels()).extracting(ErrandLabel::getId).containsExactly("label-migrated", "label-documentation");
		verify(supportManagement, never()).createInvestigation(anyString(), anyString(), any());
		verifyNoInteractions(partyAssets);
	}

	@Test
	void aDocumentationErrandWaitsForWhatItIsFiledUnder() {
		final var notificationKey = new ItemKey(Source.ALKOL, ANMALAN, "1-FOLKOL");
		lenient().when(itemRepository.findByKey(notificationKey)).thenReturn(Optional.empty());

		final var result = migrator.process(TestData.documentation("31021", notificationKey), context(false));

		assertThat(result.outcome()).isEqualTo(Outcome.VANTAR);
		assertThat(result.detail()).isEqualTo("Väntar på anmälan ALKOL/ANMALAN/1-FOLKOL, som är inte migrerat");
		assertThat(savedStatuses).containsExactly(ItemStatus.VANTAR);
	}

	@Test
	void aDocumentationErrandIsNotMigratedWithoutWhatItIsFiledUnder() {
		final var permitKey = new ItemKey(Source.ALKT, TILLSTAND, "1");
		lenient().when(itemRepository.findByKey(permitKey)).thenReturn(Optional.of(item(ItemStatus.MIGRERAS_INTE, null)));

		final var result = migrator.process(TestData.documentation("31022", permitKey), context(false));

		// An inspection errand would be migrated without a relation, but the documentation belongs to the permit (decision 71)
		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAS_INTE);
		assertThat(result.detail()).isEqualTo("Händelsen migreras inte, eftersom tillståndet ALKT/TILLSTAND/1 som den hör till inte migreras (beslut 71)");
		assertThat(savedStatuses).containsExactly(ItemStatus.MIGRERAS_INTE);
		verify(supportManagement, never()).createErrand(anyString(), any());
	}

	@Test
	void whatTheRunDidWithThePermitDecidesOverWhatAnEarlierRunSaved() {
		final var permitKey = new ItemKey(Source.ALKT, TILLSTAND, "1");
		lenient().when(itemRepository.findByKey(permitKey)).thenReturn(Optional.of(item(ItemStatus.MIGRERAS_INTE, null)));
		final var context = context(true);
		context.recordOutcome(permitKey, Outcome.GRANSKAS);

		final var result = migrator.process(TestData.documentation("31022", permitKey), context);

		// The permit was stopped in an earlier run but is to be reviewed now, so the documentation may yet follow it
		assertThat(result.outcome()).isEqualTo(Outcome.VANTAR);
		assertThat(result.detail()).isEqualTo("Väntar på tillståndet ALKT/TILLSTAND/1, som är GRANSKAS");
		verify(itemRepository, never()).save(any());
	}

	@Test
	void aMigratedDocumentationErrandGetsItsRelationWhenItsNotificationIsMigratedAfterIt() {
		final var notificationKey = new ItemKey(Source.ALKOL, ANMALAN, "1-FOLKOL");
		final var candidate = TestData.documentation("31021", notificationKey);
		when(itemRepository.findByKey(candidate.key())).thenReturn(Optional.of(item(ItemStatus.KLAR, ERRAND_ID)));
		when(itemRepository.findByKey(notificationKey)).thenReturn(Optional.of(item(ItemStatus.KLAR, PERMIT_ERRAND_ID)));
		when(relations.findRelations(MUNICIPALITY_ID, "LINK", PERMIT_ERRAND_ID, ERRAND_ID)).thenReturn(List.of());
		when(relations.createRelation(eq(MUNICIPALITY_ID), any())).thenReturn("relation-id");

		final var result = migrator.process(candidate, context(false));

		assertThat(result).isEqualTo(new ItemResult(Outcome.REDAN_MIGRERAD, List.of(), "Relation till anmälningsärendet ALKOL/ANMALAN/1-FOLKOL, tillagd i efterhand", "ALKT-2026-1"));
	}

	/**
	 * Lets the serving permits get their decisions (decision 70).
	 */
	private void withDecisions() {
		properties = TestData.propertiesWithDecisions(documents.toString(), attachments.toString(), "ab12cde");
		migrator = migrator(properties);
	}

	/**
	 * Lets permit 1 be migrated as a new draft: the certificate beslut.rtf is on the errand from an earlier run, and the
	 * application ansokan.pdf is uploaded.
	 */
	private void stubNewPermit() throws IOException {
		Files.writeString(documents.resolve("beslut.rtf"), "dokument");
		Files.writeString(attachments.resolve("ansokan.pdf"), "bilaga");
		when(supportManagement.findErrandsByExternalTag(MUNICIPALITY_ID, "migreringsid", "ALKT-TILLSTAND-1")).thenReturn(List.of());
		when(supportManagement.createErrand(eq(MUNICIPALITY_ID), any())).thenReturn(ERRAND_ID);
		when(supportManagement.getErrand(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(Optional.of(errand(LifecycleEnum.DRAFT)));
		when(supportManagement.getAttachments(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of(new ErrandAttachment().id("attachment-beslut").fileName("beslut.rtf")
			.hash(FileUploader.sha256("dokument".getBytes(StandardCharsets.UTF_8)))));
		when(partyAssets.findAsset(MUNICIPALITY_ID, PARTY_ID, ERRAND_ID)).thenReturn(Optional.empty());
		when(partyAssets.createAsset(eq(MUNICIPALITY_ID), anyString(), any())).thenReturn(Optional.of("asset-internal-id"));
		when(supportManagement.createAttachment(eq(MUNICIPALITY_ID), eq(ERRAND_ID), eq("ansokan.pdf"), any(), any())).thenReturn(Optional.of("attachment-id"));
	}

	private RunContext contextWithOutcomes(final boolean dryRun) {
		final var check = new Preflight.Result(List.of(), Map.of(MUNICIPALITY_ID, TestData.labelIds()), Map.of(MUNICIPALITY_ID, TestData.purposes()),
			Map.of(MUNICIPALITY_ID, Map.of("APPROVAL", "Bifall", "APPROVAL_WITH_CONDITIONS", "Bifall med villkor")));
		return new RunContext("run-id", MUNICIPALITY_ID, dryRun, check, null, new AtomicBoolean(), this::fileArea);
	}

	private ItemMigrator migrator(final AotLoaderProperties settings) {
		return new ItemMigrator(itemRepository, exceptionRepository, new PartyResolver(party), new RelationPlanner(itemRepository), new FileUploader(supportManagement, settings, CLOCK), new AssetFileUploader(partyAssets, settings), new LegalBasisReader(
			settings),
			new TargetWriter(supportManagement, partyAssets, relations, new TargetMapper(settings, CLOCK), new AssetMapper(settings), settings), settings, CLOCK);
	}

	/**
	 * @return the post of permit 1 as the migration creates it
	 */
	private Asset assetAsCreated(final String id, final Status status) {
		final var created = new AssetMapper(properties).toAsset(TestData.permit("1"), MUNICIPALITY_ID, ERRAND_ID, PARTY_ID, false);
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
		final var check = new Preflight.Result(List.of(), Map.of(MUNICIPALITY_ID, TestData.labelIds(), TIMRA, TestData.labelIds()), Map.of(MUNICIPALITY_ID, TestData.purposes(), TIMRA, TestData.purposes()));
		return new RunContext("run-id", MUNICIPALITY_ID, dryRun, check, null, new AtomicBoolean(), this::fileArea);
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
