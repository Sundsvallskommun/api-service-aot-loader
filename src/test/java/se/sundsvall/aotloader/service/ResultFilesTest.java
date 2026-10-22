package se.sundsvall.aotloader.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.aotloader.integration.db.RunItemRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationRunEntity;
import se.sundsvall.aotloader.integration.db.model.RunItemEntity;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.Source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultFilesTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneId.of("Europe/Stockholm"));

	@Mock
	private RunItemRepository runItemRepository;

	@TempDir
	private Path directory;

	@Test
	void theReportOfARunIsWrittenAsTheApiGivesIt() throws IOException {
		final var run = run("1a2b3c4d-0000-0000-0000-000000000000", true, false, null);
		final var line = new RunItemEntity();
		line.setSource(Source.ALKT);
		line.setKind(Kind.TILLSTAND);
		line.setSourceId("1");
		line.setOutcome(Outcome.GRANSKAS);
		line.setRules("G14");
		line.setDetail("G14 1 fil är större än 50MB och laddas inte upp (stor.pdf)");
		when(runItemRepository.findByRunIdOrderById(run.getId())).thenReturn(List.of(line));
		final var folder = directory.resolve("resultat");

		final var message = new ResultFiles(runItemRepository, folder.toString(), CLOCK).write(run);

		final var file = folder.resolve("rapport-20261022-140000-torrkorning-1a2b3c4d.csv");
		assertThat(message).contains("Rapporten finns som CSV i " + file.toAbsolutePath().normalize());
		assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(ReportCsv.write(List.of(MigrationService.toReportLine(line))));
		// No temporary file is left behind
		try (var files = Files.list(folder)) {
			assertThat(files).containsExactly(file);
		}
	}

	@Test
	void theNameTellsWhatTheRunWas() {
		final var resultFiles = new ResultFiles(runItemRepository, directory.toString(), CLOCK);

		assertThat(resultFiles.fileName(run("abcdef12-3456", false, false, null))).isEqualTo("rapport-20261022-140000-korning-abcdef12.csv");
		assertThat(resultFiles.fileName(run("abcdef12-3456", true, true, null))).isEqualTo("rapport-20261022-140000-rensning-torrkorning-abcdef12.csv");
		assertThat(resultFiles.fileName(run("abcdef12-3456", false, true, null))).isEqualTo("rapport-20261022-140000-rensning-korning-abcdef12.csv");
		assertThat(resultFiles.fileName(run("ab", false, false, "ALKOL/ANMALAN/12-FOLKOL"))).isEqualTo("rapport-20261022-140000-korning-ALKOL-ANMALAN-12-FOLKOL-ab.csv");
		assertThat(resultFiles.fileName(run("../x", false, false, "..\\..\\x"))).isEqualTo("rapport-20261022-140000-korning-x-x.csv");
	}

	@Test
	void noDirectoryWritesNoFile() {
		assertThat(new ResultFiles(runItemRepository, " ", CLOCK).write(run("a", true, false, null))).isEmpty();
		assertThat(new ResultFiles(runItemRepository, null, CLOCK).write(run("a", true, false, null))).isEmpty();
		verifyNoInteractions(runItemRepository);
	}

	@Test
	void aRunWithoutRowsWritesNoFile() throws IOException {
		final var run = run("a", false, false, null);
		when(runItemRepository.findByRunIdOrderById("a")).thenReturn(List.of());

		assertThat(new ResultFiles(runItemRepository, directory.toString(), CLOCK).write(run)).isEmpty();
		try (var files = Files.list(directory)) {
			assertThat(files).isEmpty();
		}
	}

	@Test
	void aFileThatCannotBeWrittenIsTold() throws IOException {
		final var notADirectory = Files.writeString(directory.resolve("fil.txt"), "x");
		final var line = new RunItemEntity();
		line.setSource(Source.ALKT);
		line.setKind(Kind.TILLSTAND);
		line.setSourceId("1");
		line.setOutcome(Outcome.MIGRERAD);
		when(runItemRepository.findByRunIdOrderById("a")).thenReturn(List.of(line));

		final var message = new ResultFiles(runItemRepository, notADirectory.toString(), CLOCK).write(run("a", true, false, null));

		assertThat(message).hasValueSatisfying(text -> assertThat(text).startsWith("Rapporten kunde inte skrivas till resultatkatalogen: "));
	}

	private static MigrationRunEntity run(final String id, final boolean dryRun, final boolean cleanup, final String itemKey) {
		final var run = new MigrationRunEntity();
		run.setId(id);
		run.setDryRun(dryRun);
		run.setCleanup(cleanup);
		run.setItemKey(itemKey);
		run.setStarted(OffsetDateTime.of(2026, 10, 22, 12, 0, 0, 0, ZoneOffset.UTC));
		return run;
	}
}
