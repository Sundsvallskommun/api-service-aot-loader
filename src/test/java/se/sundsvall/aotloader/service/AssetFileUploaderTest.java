package se.sundsvall.aotloader.service;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetAttachment;
import generated.se.sundsvall.partyassets.Status;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.integration.partyassets.PartyAssetsIntegration;
import se.sundsvall.aotloader.service.model.AssetData;
import se.sundsvall.aotloader.service.model.AssetFile;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.FileDetails;
import se.sundsvall.aotloader.service.model.FileReference;
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
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;

@ExtendWith(MockitoExtension.class)
class AssetFileUploaderTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String ASSET_ID = "22222222-2222-2222-2222-222222222222";

	@Mock
	private PartyAssetsIntegration partyAssets;

	@TempDir
	private Path documents;

	@TempDir
	private Path attachments;

	private AotLoaderProperties properties;
	private AssetFileUploader uploader;

	@BeforeEach
	void setUp() throws IOException {
		properties = TestData.properties(documents.toString(), attachments.toString());
		uploader = new AssetFileUploader(partyAssets, properties);
		Files.writeString(documents.resolve("beslut.rtf"), "dokument");
		Files.writeString(attachments.resolve("ansokan.pdf"), "bilaga");
	}

	@Test
	void aFileOfThePostThatCannotBeReadFailsTheStep() {
		final var gone = new FileReference("borta.pdf", ATTACHMENTS);
		final var readable = new FileReference("ansokan.pdf", ATTACHMENTS);
		final var fileArea = mock(FileArea.class);
		when(fileArea.find(gone)).thenReturn(new FileArea.Lookup(LookupResult.UNREADABLE, "borta.pdf", attachments.resolve("borta.pdf"), 0));
		when(fileArea.find(readable)).thenReturn(new FileArea.Lookup(LookupResult.FOUND, "ansokan.pdf", attachments.resolve("ansokan.pdf"), 6));
		when(partyAssets.getAttachments(MUNICIPALITY_ID, ASSET_ID)).thenReturn(List.of());
		when(partyAssets.createAttachment(eq(MUNICIPALITY_ID), eq(ASSET_ID), anyString(), any(), anyString())).thenReturn(true);
		final var candidate = withAssetFiles(new AssetFile(gone, "Ritning"), new AssetFile(readable, "Beslut"));

		assertThatThrownBy(() -> uploader.uploadToAsset(candidate, context(fileArea), MUNICIPALITY_ID, ASSET_ID, new ReportNotes()))
			.isInstanceOf(MigrationException.class)
			.hasMessage("Filer på filarean för AlkT gick inte att läsa och laddas upp i nästa körning");

		verify(partyAssets).createAttachment(MUNICIPALITY_ID, ASSET_ID, "ansokan.pdf", "bilaga".getBytes(StandardCharsets.UTF_8), "Beslut");
	}

	@Test
	void theFilesOfThePostGoThereInTheirCategoryOnceAndThoseTheErrandStepListsAreSkipped() throws IOException {
		Files.writeString(attachments.resolve("ritning.pdf"), "ritning");
		final var candidate = withAssetFiles(
			new AssetFile(new FileReference("beslut.rtf", DOCUMENTS), "Tillståndsbevis"),
			new AssetFile(new FileReference("ansokan.pdf", ATTACHMENTS), "Beslut"),
			new AssetFile(new FileReference("ritning.pdf", ATTACHMENTS), "Ritning"),
			new AssetFile(new FileReference("saknas.pdf", ATTACHMENTS), "Ritning"),
			new AssetFile(new FileReference("delad.pdf", ATTACHMENTS, true), "Beslut"));
		when(partyAssets.getAttachments(MUNICIPALITY_ID, ASSET_ID)).thenReturn(List.of(new AssetAttachment().fileName("ANSOKAN.pdf").fileSize("bilaga".length())));
		when(partyAssets.createAttachment(MUNICIPALITY_ID, ASSET_ID, "beslut.rtf", "dokument".getBytes(StandardCharsets.UTF_8), "Tillståndsbevis")).thenReturn(true);
		when(partyAssets.createAttachment(MUNICIPALITY_ID, ASSET_ID, "ritning.pdf", "ritning".getBytes(StandardCharsets.UTF_8), "Ritning")).thenReturn(false);
		when(partyAssets.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(new Asset().id(ASSET_ID).status(Status.ACTIVE)));
		final var notes = new ReportNotes();

		uploader.uploadToAsset(candidate, context(), MUNICIPALITY_ID, ASSET_ID, notes);

		verify(partyAssets, times(2)).createAttachment(eq(MUNICIPALITY_ID), eq(ASSET_ID), anyString(), any(), anyString());
		final var result = notes.result(Outcome.MIGRERAD, null);
		assertThat(result.rules()).containsExactly("G14");
		assertThat(result.detail()).isEqualTo("Tillståndet i PartyAssets har 2 av 5 filer: Tillståndsbevis 1, Beslut 2, Ritning 2; "
			+ "G14 1 fil togs inte emot av PartyAssets och ligger bara på ärendet (ritning.pdf)");
	}

	@Test
	void aFileWithoutACategoryGoesOnThePostWithoutOne() {
		final var candidate = withAssetFiles(
			new AssetFile(new FileReference("beslut.rtf", DOCUMENTS), null),
			new AssetFile(new FileReference("ansokan.pdf", ATTACHMENTS), null));
		when(partyAssets.getAttachments(MUNICIPALITY_ID, ASSET_ID)).thenReturn(List.of());
		when(partyAssets.createAttachment(eq(MUNICIPALITY_ID), eq(ASSET_ID), anyString(), any(), isNull())).thenReturn(true);
		final var notes = new ReportNotes();

		uploader.uploadToAsset(candidate, context(), MUNICIPALITY_ID, ASSET_ID, notes);

		verify(partyAssets).createAttachment(MUNICIPALITY_ID, ASSET_ID, "beslut.rtf", "dokument".getBytes(StandardCharsets.UTF_8), null);
		verify(partyAssets).createAttachment(MUNICIPALITY_ID, ASSET_ID, "ansokan.pdf", "bilaga".getBytes(StandardCharsets.UTF_8), null);
		assertThat(notes.result(Outcome.MIGRERAD, null).detail()).isEqualTo("Tillståndet i PartyAssets har 2 av 2 filer: utan kategori 2");
	}

	@Test
	void aFileWithAPurposeGetsTheNameShownOfThePurposeAsItsCategoryOnThePost() throws IOException {
		Files.writeString(attachments.resolve("ritning.pdf"), "ritning");
		final var drawing = new FileReference("ritning.pdf", ATTACHMENTS);
		final var candidate = Candidate.builder(new ItemKey(Source.ALKT, Kind.TILLSTAND, "1"), ErrandType.SERVING_PERMIT)
			.asset(new AssetData(null, "Serveringstillstånd", LocalDate.of(2019, 5, 1), null, Map.of()))
			.files(List.of(drawing))
			.assetFiles(List.of(new AssetFile(drawing, "Ritning")))
			.fileDetails(Map.of(drawing, new FileDetails(FilePurposes.FLOOR_PLAN, null)))
			.build();
		when(partyAssets.getAttachments(MUNICIPALITY_ID, ASSET_ID)).thenReturn(List.of());
		when(partyAssets.createAttachment(eq(MUNICIPALITY_ID), eq(ASSET_ID), anyString(), any(), anyString())).thenReturn(true);
		final var notes = new ReportNotes();

		uploader.uploadToAsset(candidate, context(), MUNICIPALITY_ID, ASSET_ID, notes);

		verify(partyAssets).createAttachment(MUNICIPALITY_ID, ASSET_ID, "ritning.pdf", "ritning".getBytes(StandardCharsets.UTF_8), "floor_plan");
		assertThat(notes.result(Outcome.MIGRERAD, null).detail()).isEqualTo("Tillståndet i PartyAssets har 1 av 1 filer: floor_plan 1");
	}

	@Test
	void aFileTheExpiredPostOfANotificationRefusesSpeaksOfTheNotification() {
		final var reference = new FileReference("ansokan.pdf", ATTACHMENTS);
		final var notification = Candidate.builder(new ItemKey(Source.ALKOL, Kind.ANMALAN, "1-FOLKOL"), ErrandType.FOLKOL_SALES_NOTIFICATION)
			.asset(new AssetData(null, "Anmälan", LocalDate.of(2019, 5, 1), null, Map.of()))
			.files(List.of(reference), List.of(new AssetFile(reference, null)), Map.of())
			.build();
		when(partyAssets.getAttachments(MUNICIPALITY_ID, ASSET_ID)).thenReturn(List.of());
		when(partyAssets.createAttachment(eq(MUNICIPALITY_ID), eq(ASSET_ID), anyString(), any(), isNull())).thenReturn(false);
		when(partyAssets.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(new Asset().id(ASSET_ID).status(Status.EXPIRED)));

		assertThatThrownBy(() -> uploader.uploadToAsset(notification, context(), MUNICIPALITY_ID, ASSET_ID, new ReportNotes()))
			.isInstanceOf(MigrationException.class)
			.hasMessage("Anmälan " + ASSET_ID + " i PartyAssets har status EXPIRED och tar inte emot filer");
	}

	@Test
	void aFileRefusedSinceThePostIsNoLongerActiveFailsTheStep() {
		final var candidate = withAssetFiles(new AssetFile(new FileReference("beslut.rtf", DOCUMENTS), "Tillståndsbevis"));
		when(partyAssets.getAttachments(MUNICIPALITY_ID, ASSET_ID)).thenReturn(List.of());
		when(partyAssets.createAttachment(eq(MUNICIPALITY_ID), eq(ASSET_ID), anyString(), any(), anyString())).thenReturn(false);
		when(partyAssets.getAsset(MUNICIPALITY_ID, ASSET_ID)).thenReturn(Optional.of(new Asset().id(ASSET_ID).status(Status.EXPIRED)));

		assertThatThrownBy(() -> uploader.uploadToAsset(candidate, context(), MUNICIPALITY_ID, ASSET_ID, new ReportNotes()))
			.isInstanceOf(MigrationException.class)
			.hasMessage("Tillståndet " + ASSET_ID + " i PartyAssets har status EXPIRED och tar inte emot filer");
	}

	@Test
	void theFilesMissingOnThePostAreThoseToBeUploadedThatAreNotThere() throws IOException {
		Files.writeString(attachments.resolve("ritning.pdf"), "ritning");
		final var candidate = withAssetFiles(
			new AssetFile(new FileReference("beslut.rtf", DOCUMENTS), "Tillståndsbevis"),
			new AssetFile(new FileReference("ansokan.pdf", ATTACHMENTS), "Beslut"),
			new AssetFile(new FileReference("ritning.pdf", ATTACHMENTS), "Ritning"),
			new AssetFile(new FileReference("saknas.pdf", ATTACHMENTS), "Ritning"),
			new AssetFile(new FileReference("delad.pdf", ATTACHMENTS, true), "Beslut"));
		when(partyAssets.getAttachments(MUNICIPALITY_ID, ASSET_ID)).thenReturn(List.of(
			new AssetAttachment().fileName("beslut.rtf").fileSize("dokument".length()),
			new AssetAttachment().fileName("ansokan.pdf").fileSize(1)));

		assertThat(uploader.missingOnAsset(candidate, context(), MUNICIPALITY_ID, ASSET_ID)).as("ansokan.pdf has another size, ritning.pdf is not there").isEqualTo(2);
		assertThat(uploader.missingOnAsset(TestData.permit("1"), context(), MUNICIPALITY_ID, ASSET_ID)).isZero();
		verify(partyAssets, never()).createAttachment(anyString(), anyString(), anyString(), any(), anyString());
	}

	@Test
	void aPermitWithoutFilesForThePostCallsNothing() {
		final var notes = new ReportNotes();

		uploader.uploadToAsset(TestData.permit("1"), context(), MUNICIPALITY_ID, ASSET_ID, notes);

		verifyNoInteractions(partyAssets);
		assertThat(notes.result(Outcome.MIGRERAD, null).detail()).isNull();
	}

	@Test
	void aFileAreaThatCannotBeReadStopsTheFilesOfThePost() {
		properties = TestData.properties(documents.resolve("finns-inte").toString(), attachments.toString());
		final var candidate = withAssetFiles(new AssetFile(new FileReference("beslut.rtf", DOCUMENTS), "Tillståndsbevis"));
		when(partyAssets.getAttachments(MUNICIPALITY_ID, ASSET_ID)).thenReturn(List.of());

		assertThatThrownBy(() -> uploader.uploadToAsset(candidate, context(), MUNICIPALITY_ID, ASSET_ID, new ReportNotes()))
			.isInstanceOf(MigrationException.class)
			.hasMessage("Filarean för AlkT gick inte att läsa");
		verify(partyAssets, never()).createAttachment(anyString(), anyString(), anyString(), any(), anyString());
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
