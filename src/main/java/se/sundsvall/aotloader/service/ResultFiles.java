package se.sundsvall.aotloader.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.integration.db.RunItemRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationRunEntity;
import se.sundsvall.aotloader.support.Texts;

/**
 * Writes the report of a finished run to the result directory as a CSV file for Excel, the same as the API gives, so
 * that it can be sent to the business. The file is written whole or not at all, and a run whose file cannot be written
 * still finishes.
 */
@Component
class ResultFiles {

	private static final Logger LOG = LoggerFactory.getLogger(ResultFiles.class);
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final int RUN_ID_LENGTH = 8;

	private final RunItemRepository runItemRepository;
	private final String directory;
	private final Clock clock;

	ResultFiles(final RunItemRepository runItemRepository, @Value("${aot-loader.result-directory:}") final String directory, final Clock clock) {
		this.runItemRepository = runItemRepository;
		this.directory = Texts.trimToNull(directory);
		this.clock = clock;
	}

	/**
	 * Writes the report of the run, replacing a file of the same run.
	 *
	 * @return a line for the message of the run: where the file is, or why it could not be written; empty when no result
	 *         directory is configured, and when the report has no rows, as for a run that did not start
	 */
	Optional<String> write(final MigrationRunEntity run) {
		if (directory == null) {
			return Optional.empty();
		}
		try {
			final var lines = runItemRepository.findByRunIdOrderById(run.getId()).stream().map(MigrationService::toReportLine).toList();
			if (lines.isEmpty()) {
				return Optional.empty();
			}
			final var folder = Path.of(directory).toAbsolutePath().normalize();
			Files.createDirectories(folder);
			final var file = folder.resolve(fileName(run));
			final var temporary = Files.createTempFile(folder, ".rapport-", ".tmp");
			try {
				Files.writeString(temporary, ReportCsv.write(lines), StandardCharsets.UTF_8);
				move(temporary, file);
			} finally {
				Files.deleteIfExists(temporary);
			}
			return Optional.of("Rapporten finns som CSV i " + file);
		} catch (final IOException | RuntimeException e) {
			LOG.error("The report of run {} could not be written to the result directory: {}", run.getId(), e.getClass().getSimpleName());
			return Optional.of("Rapporten kunde inte skrivas till resultatkatalogen: " + e.getClass().getSimpleName());
		}
	}

	/**
	 * @return the name of the file of the run, e.g. rapport-20261022-153000-torrkorning-1a2b3c4d.csv, by the time it
	 *         started, what it was, and the item of a run of a single item
	 */
	String fileName(final MigrationRunEntity run) {
		final var started = run.getStarted() == null ? "" : run.getStarted().atZoneSameInstant(clock.getZone()).format(TIME) + "-";
		final var what = (run.isCleanup() ? "rensning-" : "") + (run.isDryRun() ? "torrkorning" : "korning");
		final var item = Optional.ofNullable(Texts.trimToNull(run.getItemKey())).map(key -> "-" + safe(key)).orElse("");
		final var id = safe(run.getId());
		return "rapport-" + started + what + item + "-" + id.substring(0, Math.min(RUN_ID_LENGTH, id.length())) + ".csv";
	}

	private static String safe(final String text) {
		return Optional.ofNullable(text).orElse("").replaceAll("[^A-Za-z0-9]+", "-").replaceAll("(?:^-)|(?:-$)", "");
	}

	private static void move(final Path from, final Path to) throws IOException {
		try {
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (final AtomicMoveNotSupportedException e) {
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
