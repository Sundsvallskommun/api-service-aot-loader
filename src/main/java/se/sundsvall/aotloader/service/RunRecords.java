package se.sundsvall.aotloader.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.integration.db.MigrationRunRepository;
import se.sundsvall.aotloader.integration.db.RunItemRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationRunEntity;
import se.sundsvall.aotloader.integration.db.model.RunItemEntity;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.Constants.ERRAND_NUMBER_MAX_LENGTH;
import static se.sundsvall.aotloader.Constants.PLACE_NAME_MAX_LENGTH;
import static se.sundsvall.aotloader.Constants.PLACE_NUMBER_MAX_LENGTH;

/**
 * Writes the report rows of a run, and the run when it finishes, with its report as a file in the result directory. A
 * row
 * or a run that cannot be saved is logged and left out, so that the run goes on.
 */
@Component
class RunRecords {

	private static final Logger LOG = LoggerFactory.getLogger(RunRecords.class);

	private final MigrationRunRepository runRepository;
	private final RunItemRepository runItemRepository;
	private final ResultFiles resultFiles;
	private final Clock clock;

	RunRecords(final MigrationRunRepository runRepository, final RunItemRepository runItemRepository, final ResultFiles resultFiles, final Clock clock) {
		this.runRepository = runRepository;
		this.runItemRepository = runItemRepository;
		this.resultFiles = resultFiles;
		this.clock = clock;
	}

	/**
	 * @return a new report row of the run for the item, not yet saved
	 */
	RunItemEntity newLine(final MigrationRunEntity run, final ItemKey key) {
		final var line = new RunItemEntity();
		line.setRunId(run.getId());
		line.setSource(key.source());
		line.setKind(key.kind());
		line.setSourceId(key.sourceId());
		line.setCreated(OffsetDateTime.now(clock));
		return line;
	}

	/**
	 * Saves the row, its place and errand number cut to the columns. A row that cannot be saved is counted on the progress.
	 */
	void save(final RunItemEntity line, final RunProgress progress) {
		line.setPlaceName(Texts.truncate(line.getPlaceName(), PLACE_NAME_MAX_LENGTH, null));
		line.setPlaceNumber(Texts.truncate(line.getPlaceNumber(), PLACE_NUMBER_MAX_LENGTH, null));
		line.setErrandNumber(Texts.truncate(line.getErrandNumber(), ERRAND_NUMBER_MAX_LENGTH, null));
		try {
			runItemRepository.save(line);
		} catch (final RuntimeException e) {
			progress.lineNotSaved();
			LOG.error("The report row of {}/{}/{} could not be saved: {}", line.getSource(), line.getKind(), line.getSourceId(), e.getClass().getSimpleName());
		}
	}

	/**
	 * Writes the report of the run as a file, and saves the run with the status and the messages of its progress, finished
	 * now; a message tells where the file is, and one how many rows of the report could not be saved.
	 */
	void finish(final MigrationRunEntity run, final RunProgress progress) {
		if (progress.unsavedLines() > 0) {
			progress.note("%d rader kunde inte sparas och saknas i rapporten".formatted(progress.unsavedLines()));
		}
		resultFiles.write(run).ifPresent(progress::note);
		run.setStatus(progress.status());
		run.setFinished(OffsetDateTime.now(clock));
		run.setMessage(ReportNotes.reportText(Texts.join("\n", progress.messages())));
		try {
			runRepository.save(run);
			LOG.info("Run {} ({}{}) finished as {}: {}", run.getId(), run.isDryRun() ? "dry run" : "real run", run.isCleanup() ? " of a cleanup" : "", progress.status(),
				OutcomeCounts.of(runItemRepository, run.getId()));
		} catch (final RuntimeException e) {
			LOG.error("Run {} could not be saved as {}: {}", run.getId(), progress.status(), e.getClass().getSimpleName());
		}
	}
}
