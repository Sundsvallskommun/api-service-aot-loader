package se.sundsvall.aotloader.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.source.files.FileArea;
import se.sundsvall.aotloader.source.files.FileArea.LookupResult;
import se.sundsvall.aotloader.support.MigrationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;

class LegalBasisReaderTest {

	private static final String MUNICIPALITY_ID = "2281";

	@TempDir
	private Path documents;

	@TempDir
	private Path attachments;

	private LegalBasisReader reader;

	@BeforeEach
	void setUp() {
		reader = new LegalBasisReader(TestData.properties(documents.toString(), attachments.toString()));
	}

	@Test
	void aLegalBasisDocumentThatCannotBeReadFailsTheStepRatherThanLeavingThePostWithoutIt() throws IOException {
		Files.writeString(documents.resolve("bevis.rtf"), "{\\rtf1 8 kap. 2 \\'a7 alkohollagen (2010:1622)}");
		final var unreadable = new FileReference("beslut.rtf", DOCUMENTS);
		final var gone = new FileReference("borta.rtf", DOCUMENTS);
		final var certificate = new FileReference("bevis.rtf", DOCUMENTS);
		final var fileArea = mock(FileArea.class);
		when(fileArea.find(unreadable)).thenReturn(new FileArea.Lookup(LookupResult.UNREADABLE, "beslut.rtf", documents.resolve("beslut.rtf"), 0));
		// Found before the file was removed
		when(fileArea.find(gone)).thenReturn(new FileArea.Lookup(LookupResult.FOUND, "borta.rtf", documents.resolve("borta.rtf"), 6));
		when(fileArea.find(certificate)).thenReturn(new FileArea.Lookup(LookupResult.FOUND, "bevis.rtf", documents.resolve("bevis.rtf"), 50));
		final var notes = new ReportNotes();

		assertThatThrownBy(() -> reader.withLegalBasis(TestData.permitWithLegalBasisDocuments("1", unreadable, certificate), context(fileArea), notes))
			.isInstanceOf(MigrationException.class)
			.hasMessage("Tillståndets beslut eller bevis på filarean för AlkT gick inte att läsa, och lagrummet läses i nästa körning");
		assertThatThrownBy(() -> reader.withLegalBasis(TestData.permitWithLegalBasisDocuments("1", gone, certificate), context(fileArea), notes))
			.isInstanceOf(MigrationException.class);
		// A document after the one that states the legal basis is not read
		assertThat(reader.withLegalBasis(TestData.permitWithLegalBasisDocuments("1", certificate, unreadable), context(fileArea), notes).asset().parameters())
			.containsEntry("legalBasis", "8 kap. 2 § alkohollagen (2010:1622)");
	}

	@Test
	void aDryRunTellsThatALegalBasisDocumentCannotBeRead() {
		final var unreadable = new FileReference("beslut.rtf", DOCUMENTS);
		final var fileArea = mock(FileArea.class);
		when(fileArea.find(unreadable)).thenReturn(new FileArea.Lookup(LookupResult.DIRECTORY_UNAVAILABLE, "beslut.rtf", documents.resolve("beslut.rtf"), 0));
		final var candidate = TestData.permitWithLegalBasisDocuments("1", unreadable);
		final var dryRun = new RunContext("run-id", MUNICIPALITY_ID, true, check(), null, new AtomicBoolean(), _ -> fileArea);
		final var notes = new ReportNotes();

		assertThat(reader.withLegalBasis(candidate, dryRun, notes)).isSameAs(candidate);
		assertThat(notes.result(Outcome.SKULLE_MIGRERAS, null).detail()).isEqualTo("Lagrum: tillståndets beslut eller bevis gick inte att läsa");
	}

	private RunContext context(final FileArea fileArea) {
		return new RunContext("run-id", MUNICIPALITY_ID, false, check(), null, new AtomicBoolean(), _ -> fileArea);
	}

	private static Preflight.Result check() {
		return new Preflight.Result(List.of(), Map.of(), Map.of(MUNICIPALITY_ID, TestData.purposes()));
	}
}
