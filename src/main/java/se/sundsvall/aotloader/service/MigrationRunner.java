package se.sundsvall.aotloader.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.integration.db.MigrationRunRepository;
import se.sundsvall.aotloader.integration.db.RunItemRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationRunEntity;
import se.sundsvall.aotloader.integration.db.model.RunItemEntity;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.ReaderResult;
import se.sundsvall.aotloader.service.model.ReaderResult.Notice;
import se.sundsvall.aotloader.service.model.RunStatus;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.source.SourceReader;
import se.sundsvall.aotloader.source.files.FileArea;
import se.sundsvall.aotloader.support.ErrorTexts;
import se.sundsvall.aotloader.support.Texts;

/**
 * Runs the migration: the checks before the run, then each source with its items in order, permits before notifications
 * before inspection errands. Nothing an item or a source does stops the run; a run that cannot go on is marked FAILED
 * with the reason, and the lock is always released by the caller.
 */
@Component
public class MigrationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(MigrationRunner.class);
	private static final int MAX_RULES_LENGTH = 255;

	private final Map<Source, SourceReader> readers = new EnumMap<>(Source.class);
	private final ItemMigrator migrator;
	private final Preflight preflight;
	private final TargetGuard targetGuard;
	private final MigrationRunRepository runRepository;
	private final RunItemRepository runItemRepository;
	private final AotLoaderProperties properties;
	private final Clock clock;
	private final Map<String, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();

	MigrationRunner(final List<SourceReader> readers, final ItemMigrator migrator, final Preflight preflight, final TargetGuard targetGuard, final MigrationRunRepository runRepository, final RunItemRepository runItemRepository,
		final AotLoaderProperties properties, final Clock clock) {
		readers.forEach(reader -> this.readers.put(reader.source(), reader));
		this.migrator = migrator;
		this.preflight = preflight;
		this.targetGuard = targetGuard;
		this.runRepository = runRepository;
		this.runItemRepository = runItemRepository;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * @return the kinds each source has
	 */
	public Map<Source, Set<Kind>> supportedKinds() {
		final var kinds = new EnumMap<Source, Set<Kind>>(Source.class);
		readers.forEach((source, reader) -> kinds.put(source, reader.kinds()));
		return kinds;
	}

	/**
	 * Asks a run to stop before its next item.
	 *
	 * @return true when the run was running
	 */
	public boolean requestStop(final String runId) {
		return Optional.ofNullable(stopFlags.get(runId)).map(flag -> {
			flag.set(true);
			return true;
		}).orElse(false);
	}

	/**
	 * Runs the migration of the scope, or of the single item when one is given. Never throws, except for an
	 * {@link Error}, after which the run is still saved as FAILED.
	 *
	 * @param  run    the run, saved as RUNNING
	 * @param  scope  the sources and their kinds
	 * @param  single the one item to migrate, empty for every item of the scope
	 * @return        the result of the single item, whatever its outcome; empty for a full run, and when the item was not
	 *                found or the run stopped or failed before it
	 */
	public Optional<ItemResult> execute(final MigrationRunEntity run, final Map<Source, Set<Kind>> scope, final Optional<ItemKey> single) {
		final var stop = stopFlags.computeIfAbsent(run.getId(), _ -> new AtomicBoolean());
		final var progress = new Progress();
		try {
			checkBeforeRun(run, scope, progress).ifPresent(check -> {
				final var context = new RunContext(run.getId(), run.getMunicipalityId(), run.isDryRun(), check, run.getItemLimit(), stop, fileAreaFactory());
				migrate(run, scope, single, context, progress);
			});
		} catch (final RuntimeException e) {
			LOG.error("Run {} failed: {}", run.getId(), e.getClass().getSimpleName(), e);
			progress.fail("Körningen avbröts av ett oväntat fel: " + ErrorTexts.describe(e));
		} catch (final Error e) {
			progress.fail("Körningen avbröts av ett oväntat fel: " + e.getClass().getSimpleName());
			throw e;
		} finally {
			stopFlags.remove(run.getId());
			finish(run, progress);
		}
		return Optional.ofNullable(progress.singleResult);
	}

	/**
	 * Checks the target and the configuration. A problem stops a real run, while a dry run goes on and reports it.
	 *
	 * @return what the check found, empty when the run is not to start
	 */
	private Optional<Preflight.Result> checkBeforeRun(final MigrationRunEntity run, final Map<Source, Set<Kind>> scope, final Progress progress) {
		final var wrongTarget = targetGuard.check();
		if (wrongTarget.isPresent()) {
			progress.fail("Körningen startade inte. " + wrongTarget.get());
			return Optional.empty();
		}
		final var check = preflight.check(run.getMunicipalityId(), scope);
		if (!check.problems().isEmpty()) {
			final var problems = String.join("; ", check.problems());
			if (!run.isDryRun()) {
				progress.fail("Körningen startade inte. Kontrollen före körning: " + problems);
				return Optional.empty();
			}
			progress.note("Kontrollen före körning, som stoppar en skarp körning: " + problems);
		}
		return Optional.of(check);
	}

	private void migrate(final MigrationRunEntity run, final Map<Source, Set<Kind>> scope, final Optional<ItemKey> single, final RunContext context, final Progress progress) {
		for (final var source : Source.values()) {
			if (!migrateSource(run, source, scope.getOrDefault(source, Set.of()), single, context, progress)) {
				break;
			}
		}
		if (progress.limitReached) {
			progress.note("Gränsen på %d ärenden nåddes; resten tas i nästa körning".formatted(run.getItemLimit()));
		}
		if (single.isPresent() && progress.singleResult == null && progress.status == RunStatus.COMPLETED) {
			progress.note("Ärendet %s finns inte i källan, eller är inte ett ärende som migreras".formatted(single.get()));
		}
	}

	/**
	 * Reads the kinds of a source and migrates their items. A source that cannot be read is reported and skipped.
	 *
	 * @return false when the run was asked to stop
	 */
	private boolean migrateSource(final MigrationRunEntity run, final Source source, final Set<Kind> kinds, final Optional<ItemKey> single, final RunContext context, final Progress progress) {
		if (kinds.isEmpty()) {
			return true;
		}
		final var result = read(source, kinds, progress);
		if (result.isEmpty()) {
			return true;
		}
		if (single.isEmpty()) {
			result.get().notices().forEach(notice -> saveLine(run, notice));
		}
		return migrateItems(run, result.get().candidates(), single, context, progress);
	}

	/**
	 * Migrates the items of a source, or only the single item when one is given.
	 *
	 * @return false when the run was asked to stop
	 */
	private boolean migrateItems(final MigrationRunEntity run, final List<Candidate> candidates, final Optional<ItemKey> single, final RunContext context, final Progress progress) {
		final var selected = single.map(key -> candidates.stream().filter(candidate -> key.equals(candidate.key())).toList()).orElse(candidates);
		for (final var candidate : selected) {
			if (context.stopRequested()) {
				progress.stop();
				return false;
			}
			final var result = migrator.process(candidate, context);
			context.recordOutcome(candidate.key(), result.outcome());
			if (result.outcome() == Outcome.EJ_KORD) {
				progress.limitReached = true;
			} else {
				saveLine(run, candidate, result);
				if (single.isPresent()) {
					progress.singleResult = result;
				}
			}
		}
		return true;
	}

	private Optional<ReaderResult> read(final Source source, final Set<Kind> kinds, final Progress progress) {
		final var reader = readers.get(source);
		if (reader == null) {
			progress.fail("Det finns ingen läsare för " + source.getDisplayName());
			return Optional.empty();
		}
		try {
			return Optional.of(reader.read(kinds));
		} catch (final RuntimeException e) {
			LOG.error("Source {} could not be read: {}", source, e.getClass().getSimpleName());
			progress.fail("%s kunde inte läsas: %s".formatted(source.getDisplayName(), ErrorTexts.describe(e)));
			return Optional.empty();
		}
	}

	private Function<Source, FileArea> fileAreaFactory() {
		return source -> new FileArea(properties.fileArea(source), properties.maxFileSize().toBytes());
	}

	private void saveLine(final MigrationRunEntity run, final Candidate candidate, final ItemResult result) {
		final var line = newLine(run, candidate.key());
		line.setOutcome(result.outcome());
		line.setRules(Texts.truncate(String.join(",", result.rules()), MAX_RULES_LENGTH, null));
		line.setDetail(ReportNotes.reportText(result.detail()));
		line.setPlaceName(candidate.placeName());
		line.setPlaceNumber(candidate.placeNumber());
		line.setErrandNumber(result.errandNumber());
		save(line);
	}

	private void saveLine(final MigrationRunEntity run, final Notice notice) {
		final var line = newLine(run, notice.key());
		line.setOutcome(notice.outcome());
		line.setDetail(ReportNotes.reportText(notice.detail()));
		line.setPlaceName(notice.placeName());
		save(line);
	}

	private RunItemEntity newLine(final MigrationRunEntity run, final ItemKey key) {
		final var line = new RunItemEntity();
		line.setRunId(run.getId());
		line.setSource(key.source());
		line.setKind(key.kind());
		line.setSourceId(key.sourceId());
		line.setCreated(OffsetDateTime.now(clock));
		return line;
	}

	private void save(final RunItemEntity line) {
		try {
			runItemRepository.save(line);
		} catch (final RuntimeException e) {
			LOG.error("The report row of {}/{}/{} could not be saved: {}", line.getSource(), line.getKind(), line.getSourceId(), e.getClass().getSimpleName());
		}
	}

	private void finish(final MigrationRunEntity run, final Progress progress) {
		run.setStatus(progress.status);
		run.setFinished(OffsetDateTime.now(clock));
		run.setMessage(ReportNotes.reportText(Texts.join("\n", progress.messages)));
		try {
			runRepository.save(run);
			LOG.info("Run {} ({}) finished as {}: {}", run.getId(), run.isDryRun() ? "dry run" : "real run", progress.status, OutcomeCounts.of(runItemRepository.findByRunIdOrderById(run.getId())));
		} catch (final RuntimeException e) {
			LOG.error("Run {} could not be saved as {}: {}", run.getId(), progress.status, e.getClass().getSimpleName());
		}
	}

	/**
	 * How far a run has come: its status, its messages and, for a single item, the item's result. A run that failed stays
	 * FAILED when it is also stopped.
	 */
	private static final class Progress {

		private final List<String> messages = new ArrayList<>();
		private RunStatus status = RunStatus.COMPLETED;
		private boolean limitReached;
		private ItemResult singleResult;

		void note(final String message) {
			messages.add(message);
		}

		void fail(final String message) {
			messages.add(message);
			status = RunStatus.FAILED;
		}

		void stop() {
			messages.add("Körningen stoppades på begäran");
			if (status == RunStatus.COMPLETED) {
				status = RunStatus.STOPPED;
			}
		}
	}
}
