package se.sundsvall.aotloader.service;

import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandAttachmentPurpose;
import generated.se.sundsvall.supportmanagement.Investigation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.aotloader.service.model.AssetData;
import se.sundsvall.aotloader.service.model.AssetFile;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.FileDetails;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.InvestigationData;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.source.FilePurposes;
import se.sundsvall.aotloader.source.files.FileArea;
import se.sundsvall.aotloader.source.files.FileArea.LookupResult;
import se.sundsvall.aotloader.support.MigrationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static se.sundsvall.aotloader.configuration.ClockConfiguration.SWEDISH_TIME;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;

@ExtendWith(MockitoExtension.class)
class FileUploaderTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String ERRAND_ID = "11111111-1111-1111-1111-111111111111";
	private static final String ATTACHMENT_ID = "33333333-3333-3333-3333-333333333333";
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), SWEDISH_TIME);

	@Mock
	private SupportManagementIntegration supportManagement;

	@TempDir
	private Path documents;

	@TempDir
	private Path attachments;

	private AotLoaderProperties properties;
	private FileUploader uploader;

	@BeforeEach
	void setUp() throws IOException {
		properties = TestData.properties(documents.toString(), attachments.toString());
		uploader = new FileUploader(supportManagement, properties, CLOCK);
		Files.writeString(documents.resolve("beslut.rtf"), "dokument");
		Files.writeString(attachments.resolve("ansokan.pdf"), "bilaga");
	}

	@Test
	void aFileOnTheErrandWithoutHashIsRecognizedByItsSize() {
		when(supportManagement.getAttachments(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of(
			new ErrandAttachment().fileName("beslut.rtf").fileSize("dokument".length()),
			new ErrandAttachment().fileName("ansokan.pdf").fileSize(1)));
		when(supportManagement.createAttachment(eq(MUNICIPALITY_ID), eq(ERRAND_ID), anyString(), any(), any())).thenReturn(Optional.of(ATTACHMENT_ID));
		final var notes = new ReportNotes();

		uploader.upload(TestData.permit("1"), context(), MUNICIPALITY_ID, ERRAND_ID, notes);

		verify(supportManagement).createAttachment(eq(MUNICIPALITY_ID), eq(ERRAND_ID), anyString(), any(), any());
		verify(supportManagement).createAttachment(MUNICIPALITY_ID, ERRAND_ID, "ansokan.pdf", "bilaga".getBytes(StandardCharsets.UTF_8), null);
		assertThat(notes.result(Outcome.MIGRERAD, null).detail()).isNull();
	}

	@Test
	void theUploadTellsTheAttachmentsOfTheMaterialByNameAndContent() throws IOException {
		Files.writeString(documents.resolve("rapport.pdf"), "x");
		Files.writeString(attachments.resolve("rapport.pdf"), "yy");
		final var candidate = withInvestigation(Map.of(
			new FileReference("beslut.rtf", DOCUMENTS), new FileDetails(null, null, true),
			new FileReference("rapport.pdf", ATTACHMENTS), new FileDetails(null, null, true),
			// Another file of the same name, which is no material
			new FileReference("rapport.pdf", DOCUMENTS), FileDetails.NONE));
		// The decision is on the errand from an earlier run
		when(supportManagement.getAttachments(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of(new ErrandAttachment().id("a1").fileName("beslut.rtf").fileSize("dokument".length())));
		when(supportManagement.createAttachment(MUNICIPALITY_ID, ERRAND_ID, "rapport.pdf", "yy".getBytes(StandardCharsets.UTF_8), null)).thenReturn(Optional.of("a2"));
		when(supportManagement.createAttachment(MUNICIPALITY_ID, ERRAND_ID, "rapport.pdf", "x".getBytes(StandardCharsets.UTF_8), null)).thenReturn(Optional.of("a3"));

		final var uploaded = uploader.upload(candidate, context(), MUNICIPALITY_ID, ERRAND_ID, new ReportNotes());

		assertThat(uploaded.material()).containsExactlyInAnyOrder("a1", "a2");
		// Every file on the errand has its attachment, material or not, which the decision links its documents by
		assertThat(uploaded.attachmentIds()).containsOnly(entry(new FileReference("beslut.rtf", DOCUMENTS), "a1"), entry(new FileReference("rapport.pdf", ATTACHMENTS), "a2"),
			entry(new FileReference("rapport.pdf", DOCUMENTS), "a3"));
		assertThat(uploaded.idsOf(List.of(new FileReference("rapport.pdf", DOCUMENTS), new FileReference("saknas.pdf", DOCUMENTS), new FileReference("beslut.rtf", DOCUMENTS)))).containsExactly("a3", "a1");
	}

	@Test
	void aFileThatSupportManagementRefusesIsListed() {
		when(supportManagement.getAttachments(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of());
		when(supportManagement.createAttachment(eq(MUNICIPALITY_ID), eq(ERRAND_ID), anyString(), any(), any())).thenReturn(Optional.empty());
		final var notes = new ReportNotes();

		uploader.upload(TestData.permit("1"), context(), MUNICIPALITY_ID, ERRAND_ID, notes);

		final var result = notes.result(Outcome.MIGRERAD, null);
		assertThat(result.rules()).containsExactly("G14");
		assertThat(result.detail()).isEqualTo("G14 2 filer togs inte emot av SupportManagement (beslut.rtf, ansokan.pdf)");
	}

	@Test
	void aFileThatCannotBeReadFailsTheStepSoThatTheNextRunUploadsItAndOneThatHasGrownIsListed() throws IOException {
		Files.writeString(attachments.resolve("stor.pdf"), "x".repeat(101));
		final var gone = new FileReference("borta.pdf", ATTACHMENTS);
		final var grown = new FileReference("stor.pdf", ATTACHMENTS);
		final var readable = new FileReference("ansokan.pdf", ATTACHMENTS);
		final var fileArea = mock(FileArea.class);
		// Found before the file was removed or had grown
		when(fileArea.find(gone)).thenReturn(new FileArea.Lookup(LookupResult.FOUND, "borta.pdf", attachments.resolve("borta.pdf"), 6));
		when(fileArea.find(grown)).thenReturn(new FileArea.Lookup(LookupResult.FOUND, "stor.pdf", attachments.resolve("stor.pdf"), 6));
		when(fileArea.find(readable)).thenReturn(new FileArea.Lookup(LookupResult.FOUND, "ansokan.pdf", attachments.resolve("ansokan.pdf"), 6));
		when(supportManagement.getAttachments(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of());
		when(supportManagement.createAttachment(eq(MUNICIPALITY_ID), eq(ERRAND_ID), anyString(), any(), any())).thenReturn(Optional.of(ATTACHMENT_ID));
		final var notes = new ReportNotes();

		assertThatThrownBy(() -> uploader.upload(withFiles(gone, grown, readable), context(fileArea), MUNICIPALITY_ID, ERRAND_ID, notes))
			.isInstanceOf(MigrationException.class)
			.hasMessage("Filer på filarean för AlkT gick inte att läsa och laddas upp i nästa körning");

		verify(supportManagement).createAttachment(MUNICIPALITY_ID, ERRAND_ID, "ansokan.pdf", "bilaga".getBytes(StandardCharsets.UTF_8), null);
		verify(supportManagement, times(1)).createAttachment(anyString(), anyString(), anyString(), any(), any());
		assertThat(notes.result(Outcome.MIGRERAD, null).detail())
			.isEqualTo("G14 1 fil är större än 0 MB och laddas inte upp (stor.pdf); G14 1 fil gick inte att läsa (borta.pdf)");
	}

	@Test
	void theMaterialOfTheInvestigationIsLinkedToItOnce() {
		final var candidate = withInvestigation(Map.of(
			new FileReference("Remissvar.pdf", ATTACHMENTS), new FileDetails(FilePurposes.POLICE_RESPONSE, null, true),
			// A reference with a path names the file at its end, as the file area finds it
			new FileReference("C:\\gammal\\utredning.rtf", DOCUMENTS), new FileDetails(null, null, true),
			new FileReference("saknas.rtf", DOCUMENTS), new FileDetails(null, null, true),
			new FileReference("ansokan.pdf", ATTACHMENTS), FileDetails.NONE));
		// The investigation has the answer linked already, from an earlier run
		when(supportManagement.getInvestigations(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of(new Investigation().id("investigation-1")
			.attachments(List.of(new ErrandAttachment().id("a1").fileName("remissvar.pdf")))));
		final var notes = new ReportNotes();

		// The attachments of the material, as the upload gives them
		uploader.linkToInvestigation(candidate, MUNICIPALITY_ID, ERRAND_ID, Set.of("a1", "a2"), notes);

		verify(supportManagement).linkInvestigationAttachment(MUNICIPALITY_ID, ERRAND_ID, "investigation-1", "a2");
		verify(supportManagement, times(1)).linkInvestigationAttachment(anyString(), anyString(), anyString(), anyString());
		assertThat(notes.result(Outcome.MIGRERAD, null).detail()).isEqualTo("Underlag till utredningen: 2 filer");
	}

	@Test
	void nothingIsLinkedWithoutMaterialOrAnInvestigation() {
		final var material = Map.of(new FileReference("utredning.rtf", DOCUMENTS), new FileDetails(null, null, true));
		final var notes = new ReportNotes();

		// A candidate without an investigation, one without material, and an errand whose investigation is missing
		uploader.linkToInvestigation(withDetails(material), MUNICIPALITY_ID, ERRAND_ID, Set.of("a1"), notes);
		uploader.linkToInvestigation(withInvestigation(Map.of(new FileReference("ansokan.pdf", ATTACHMENTS), FileDetails.NONE)), MUNICIPALITY_ID, ERRAND_ID, Set.of(), notes);
		when(supportManagement.getInvestigations(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of());
		uploader.linkToInvestigation(withInvestigation(material), MUNICIPALITY_ID, ERRAND_ID, Set.of("a1"), notes);

		verify(supportManagement, never()).getAttachments(anyString(), anyString());
		verify(supportManagement, never()).linkInvestigationAttachment(anyString(), anyString(), anyString(), anyString());
		assertThat(notes.result(Outcome.MIGRERAD, null).detail()).isNull();
	}

	@Test
	void aDryRunTellsHowManyFilesWouldBeMaterialOfTheInvestigation() {
		final var candidate = withInvestigation(Map.of(
			new FileReference("beslut.rtf", DOCUMENTS), new FileDetails(null, null, true),
			new FileReference("saknas.rtf", DOCUMENTS), new FileDetails(null, null, true),
			new FileReference("ansokan.pdf", ATTACHMENTS), FileDetails.NONE));
		final var notes = new ReportNotes();

		uploader.check(candidate, context(), notes);

		assertThat(notes.result(Outcome.SKULLE_MIGRERAS, null).detail()).isEqualTo("Underlag till utredningen: 1 fil; G14 1 fil saknas på filarean (saknas.rtf)");
	}

	@Test
	void aDryRunChecksTheFilesWithoutUploading() throws IOException {
		Files.delete(attachments.resolve("ansokan.pdf"));
		final var notes = new ReportNotes();

		uploader.check(TestData.permit("1"), context(), notes);

		assertThat(notes.result(Outcome.SKULLE_MIGRERAS, null).detail()).isEqualTo("G14 1 fil saknas på filarean (ansokan.pdf)");
		verifyNoInteractions(supportManagement);
	}

	@Test
	void aFileWhoseNameAnotherPlaceAlsoUsesIsListedAndNotUploaded() throws IOException {
		Files.writeString(attachments.resolve("delad.pdf"), "kanske ett annat ställes");
		Files.writeString(attachments.resolve("delad.url"), "[InternetShortcut]");
		final var candidate = withFiles(
			new FileReference("beslut.rtf", DOCUMENTS),
			new FileReference("delad.pdf", ATTACHMENTS, true),
			new FileReference("saknas.pdf", ATTACHMENTS, true),
			new FileReference("delad.url", ATTACHMENTS, true));
		when(supportManagement.getAttachments(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of());
		when(supportManagement.createAttachment(eq(MUNICIPALITY_ID), eq(ERRAND_ID), anyString(), any(), any())).thenReturn(Optional.of(ATTACHMENT_ID));
		final var notes = new ReportNotes();

		uploader.upload(candidate, context(), MUNICIPALITY_ID, ERRAND_ID, notes);

		verify(supportManagement).createAttachment(eq(MUNICIPALITY_ID), eq(ERRAND_ID), anyString(), any(), any());
		verify(supportManagement).createAttachment(MUNICIPALITY_ID, ERRAND_ID, "beslut.rtf", "dokument".getBytes(StandardCharsets.UTF_8), null);
		final var result = notes.result(Outcome.MIGRERAD, null);
		assertThat(result.rules()).containsExactly("G14");
		assertThat(result.detail()).isEqualTo("1 länk hoppas över (delad.url); G14 2 filer har namn som också används på andra ställen och laddas inte upp (delad.pdf, saknas.pdf)");
	}

	@Test
	void aDryRunListsAFileWhoseNameAnotherPlaceAlsoUses() {
		final var notes = new ReportNotes();

		uploader.check(withFiles(new FileReference("beslut.rtf", DOCUMENTS), new FileReference("ansokan.pdf", ATTACHMENTS, true)), context(), notes);

		assertThat(notes.result(Outcome.SKULLE_MIGRERAS, null).detail()).isEqualTo("G14 1 fil har ett namn som också används på ett annat ställe och laddas inte upp (ansokan.pdf)");
		verifyNoInteractions(supportManagement);
	}

	@Test
	void aFileGetsItsPurposeAndTheTimeItCameInButNoTimeLaterThanNow() {
		final var candidate = withDetails(Map.of(
			new FileReference("beslut.rtf", DOCUMENTS), new FileDetails(FilePurposes.POLICE_REQUEST, LocalDateTime.of(2019, 6, 1, 9, 30)),
			new FileReference("ansokan.pdf", ATTACHMENTS), new FileDetails(null, LocalDateTime.of(2030, 1, 1, 0, 0))));
		when(supportManagement.getAttachments(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of());
		when(supportManagement.createAttachment(eq(MUNICIPALITY_ID), eq(ERRAND_ID), anyString(), any(), any())).thenReturn(Optional.of(ATTACHMENT_ID));

		uploader.upload(candidate, context(), MUNICIPALITY_ID, ERRAND_ID, new ReportNotes());

		verify(supportManagement).createAttachment(MUNICIPALITY_ID, ERRAND_ID, "beslut.rtf", "dokument".getBytes(StandardCharsets.UTF_8), OffsetDateTime.parse("2019-06-01T09:30+02:00"));
		verify(supportManagement).updateAttachment(MUNICIPALITY_ID, ERRAND_ID, ATTACHMENT_ID, "purpose-" + FilePurposes.POLICE_REQUEST, null);
		// SupportManagement refuses a time later than now, so the file gets the time of upload
		verify(supportManagement).createAttachment(MUNICIPALITY_ID, ERRAND_ID, "ansokan.pdf", "bilaga".getBytes(StandardCharsets.UTF_8), null);
		verify(supportManagement, times(1)).updateAttachment(anyString(), anyString(), anyString(), any(), any());
	}

	@Test
	void aFileAnEarlierRunUploadedGetsThePurposeAndTimeItLacks() {
		final var received = LocalDateTime.of(2019, 6, 1, 9, 30);
		final var candidate = withDetails(Map.of(
			new FileReference("beslut.rtf", DOCUMENTS), new FileDetails(FilePurposes.POLICE_REQUEST, received),
			new FileReference("ansokan.pdf", ATTACHMENTS), new FileDetails(FilePurposes.STATEMENT_RESPONSE, received)));
		when(supportManagement.getAttachments(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of(
			new ErrandAttachment().id("a-1").fileName("beslut.rtf").hash(FileUploader.sha256("dokument".getBytes(StandardCharsets.UTF_8))).received(OffsetDateTime.parse("2026-10-14T15:04Z")),
			new ErrandAttachment().id("a-2").fileName("ansokan.pdf").hash(FileUploader.sha256("bilaga".getBytes(StandardCharsets.UTF_8)))
				.purpose(new ErrandAttachmentPurpose().id("purpose-" + FilePurposes.STATEMENT_RESPONSE)).received(OffsetDateTime.parse("2019-06-01T07:30Z"))));

		uploader.upload(candidate, context(), MUNICIPALITY_ID, ERRAND_ID, new ReportNotes());

		verify(supportManagement).updateAttachment(MUNICIPALITY_ID, ERRAND_ID, "a-1", "purpose-" + FilePurposes.POLICE_REQUEST, OffsetDateTime.parse("2019-06-01T09:30+02:00"));
		verify(supportManagement, times(1)).updateAttachment(anyString(), anyString(), anyString(), any(), any());
		verify(supportManagement, never()).createAttachment(anyString(), anyString(), anyString(), any(), any());
	}

	@Test
	void aPurposeNotReadForTheMunicipalityStopsTheFiles() {
		final var candidate = withDetails(Map.of(new FileReference("beslut.rtf", DOCUMENTS), new FileDetails(FilePurposes.POLICE_REQUEST, null)));
		final var context = new RunContext("run-id", MUNICIPALITY_ID, false, new Preflight.Result(List.of(), Map.of(), Map.of()), null, new AtomicBoolean(),
			source -> new FileArea(properties.fileArea(source), properties.maxFileSize().toBytes()));
		when(supportManagement.getAttachments(MUNICIPALITY_ID, ERRAND_ID)).thenReturn(List.of());
		final var notes = new ReportNotes();

		assertThatThrownBy(() -> uploader.upload(candidate, context, MUNICIPALITY_ID, ERRAND_ID, notes))
			.isInstanceOf(MigrationException.class)
			.hasMessage("Syftet REFERRAL_POLICE_REQUEST för bilagor är inte läst för kommun 2281");
		verify(supportManagement, never()).createAttachment(anyString(), anyString(), anyString(), any(), any());
	}

	@Test
	void aDryRunOfANotificationSpeaksOfTheNotification() {
		final var reference = new FileReference("ansokan.pdf", ATTACHMENTS);
		final var notification = Candidate.builder(new ItemKey(Source.ALKOL, Kind.ANMALAN, "1-FOLKOL"), ErrandType.FOLKOL_SALES_NOTIFICATION)
			.asset(new AssetData(null, "Anmälan", LocalDate.of(2019, 5, 1), null, Map.of()))
			.files(List.of(reference), List.of(new AssetFile(reference, null)), Map.of())
			.build();
		final var dryRun = new ReportNotes();

		uploader.check(notification, context(), dryRun);

		assertThat(dryRun.result(Outcome.SKULLE_MIGRERAS, null).detail()).isEqualTo("Till anmälan i PartyAssets: utan kategori 1");
	}

	@Test
	void aDryRunTellsWhichFilesWouldGoOnThePost() throws IOException {
		Files.writeString(attachments.resolve("anmalan.pdf"), "anmälan");
		final var notes = new ReportNotes();

		uploader.check(withAssetFiles(new AssetFile(new FileReference("beslut.rtf", DOCUMENTS), "Tillståndsbevis"),
			new AssetFile(new FileReference("ansokan.pdf", ATTACHMENTS), "Ritning"),
			new AssetFile(new FileReference("anmalan.pdf", ATTACHMENTS), null)), context(), notes);

		assertThat(notes.result(Outcome.SKULLE_MIGRERAS, null).detail()).isEqualTo("Till tillståndet i PartyAssets: Tillståndsbevis 1, Ritning 1, utan kategori 1");
		verifyNoInteractions(supportManagement);
	}

	@Test
	void sha256() {
		assertThat(FileUploader.sha256("abc".getBytes(StandardCharsets.UTF_8))).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	private static Candidate withFiles(final FileReference... files) {
		return Candidate.builder(new ItemKey(Source.ALKT, Kind.TILLSTAND, "1"), ErrandType.SERVING_PERMIT).files(List.of(files)).build();
	}

	/**
	 * @return a permit with a post in PartyAssets, whose files are those of the errand
	 */
	private static Candidate withAssetFiles(final AssetFile... files) {
		return Candidate.builder(new ItemKey(Source.ALKT, Kind.TILLSTAND, "1"), ErrandType.SERVING_PERMIT)
			.asset(new AssetData(null, "Serveringstillstånd", LocalDate.of(2019, 5, 1), null, Map.of()))
			.files(Arrays.stream(files).map(AssetFile::reference).toList())
			.assetFiles(List.of(files))
			.build();
	}

	/**
	 * @return a permit whose files are those given, each with its purpose and time
	 */
	private static Candidate withDetails(final Map<FileReference, FileDetails> details) {
		return Candidate.builder(new ItemKey(Source.ALKT, Kind.TILLSTAND, "1"), ErrandType.SERVING_PERMIT)
			.files(List.copyOf(details.keySet()))
			.fileDetails(details)
			.build();
	}

	/**
	 * @return a permit with an investigation, whose files are those given, each with its purpose and time
	 */
	private static Candidate withInvestigation(final Map<FileReference, FileDetails> details) {
		return Candidate.builder(new ItemKey(Source.ALKT, Kind.TILLSTAND, "1"), ErrandType.SERVING_PERMIT)
			.files(List.copyOf(details.keySet()))
			.fileDetails(details)
			.investigation(new InvestigationData("Serveringstillstånd – Restaurang 1", LocalDateTime.of(2019, 1, 15, 0, 0), LocalDateTime.of(2019, 5, 1, 0, 0), InvestigationTexts.NONE))
			.build();
	}

	private RunContext context(final FileArea fileArea) {
		return new RunContext("run-id", MUNICIPALITY_ID, false, check(), null, new AtomicBoolean(), _ -> fileArea);
	}

	private RunContext context() {
		return new RunContext("run-id", MUNICIPALITY_ID, false, check(), null, new AtomicBoolean(), source -> new FileArea(properties.fileArea(source), properties.maxFileSize().toBytes()));
	}

	private static Preflight.Result check() {
		return new Preflight.Result(List.of(), Map.of(), Map.of(MUNICIPALITY_ID, TestData.purposes()));
	}
}
