package se.sundsvall.aotloader.service;

import java.util.EnumMap;
import java.util.HashSet;
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
import se.sundsvall.aotloader.integration.db.model.MigrationRunEntity;
import se.sundsvall.aotloader.integration.db.model.RunItemEntity;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.ItemSelection;
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
	private final RunRecords records;
	private final AotLoaderProperties properties;
	private final Map<String, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();

	MigrationRunner(final List<SourceReader> readers, final ItemMigrator migrator, final Preflight preflight, final TargetGuard targetGuard, final RunRecords records, final AotLoaderProperties properties) {
		readers.forEach(reader -> this.readers.put(reader.source(), reader));
		this.migrator = migrator;
		this.preflight = preflight;
		this.targetGuard = targetGuard;
		this.records = records;
		this.properties = properties;
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
	 * Makes the stop flag of a run before the run starts, so that a stop asked for while the run waits for its thread is
	 * taken before its first item.
	 */
	public void register(final String runId) {
		stopFlags.computeIfAbsent(runId, _ -> new AtomicBoolean());
	}

	/**
	 * Removes the stop flag of a run that never started.
	 */
	public void forget(final String runId) {
		stopFlags.remove(runId);
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
	 * Runs the migration of the scope, of the items the run is narrowed to, or of the single item when one is given. Never
	 * throws, except for an {@link Error}, after which the run is still saved as FAILED.
	 *
	 * @param  run    the run, saved as RUNNING, with the items it is narrowed to, if any
	 * @param  scope  the sources and their kinds
	 * @param  single the one item to migrate, empty for every item of the scope or of the run's selection
	 * @return        the row of the report of the single item, whatever its outcome, also when it could not be saved; empty
	 *                for a full run, and when the item was not found or the run stopped or failed before it
	 */
	public Optional<RunItemEntity> execute(final MigrationRunEntity run, final Map<Source, Set<Kind>> scope, final Optional<ItemKey> single) {
		final var stop = stopFlags.computeIfAbsent(run.getId(), _ -> new AtomicBoolean());
		final var progress = new Progress();
		try {
			checkBeforeRun(run, scope, progress).ifPresent(check -> {
				final var context = new RunContext(run.getId(), run.getMunicipalityId(), run.isDryRun(), check, run.getItemLimit(), stop, fileAreaFactory());
				migrate(run, scope, single, context, progress);
			});
		} catch (final RuntimeException e) {
			LOG.error("Run {} failed: {}", run.getId(), e.getClass().getSimpleName(), ErrorTexts.forLog(e));
			progress.fail("Körningen avbröts av ett oväntat fel: " + ErrorTexts.describe(e));
		} catch (final Error e) {
			progress.fail("Körningen avbröts av ett oväntat fel: " + e.getClass().getSimpleName());
			throw e;
		} finally {
			stopFlags.remove(run.getId());
			records.finish(run, progress);
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
		final var selection = single.map(key -> ItemSelection.of(List.of(key))).orElseGet(() -> ItemSelection.parse(run.getItemKeys()));
		for (final var source : Source.values()) {
			if (!migrateSource(run, source, scope.getOrDefault(source, Set.of()), selection, single.isPresent(), context, progress)) {
				break;
			}
		}
		if (progress.limitReached) {
			progress.note("Gränsen på %d ärenden nåddes; resten tas i nästa körning".formatted(run.getItemLimit()));
		}
		if (progress.status() != RunStatus.COMPLETED) {
			return;
		}
		if (single.isPresent() && progress.singleResult == null) {
			progress.note("Ärendet %s finns inte i källan, eller är inte ett ärende som migreras".formatted(single.get()));
		}
		final var missing = single.isPresent() ? List.<ItemKey>of() : selection.missing(progress.found);
		if (!missing.isEmpty()) {
			progress.note("%d av ärendena i urvalet finns inte i källan, eller är inte ärenden som migreras: %s".formatted(missing.size(), ItemSelection.listText(missing)));
		}
	}

	/**
	 * Reads the kinds of a source and migrates their selected items. A source that cannot be read is reported and skipped.
	 *
	 * @param  single true for the run of a single item, which reports only the item
	 * @return        false when the run was asked to stop
	 */
	private boolean migrateSource(final MigrationRunEntity run, final Source source, final Set<Kind> kinds, final ItemSelection selection, final boolean single, final RunContext context,
		final Progress progress) {
		if (kinds.isEmpty()) {
			return true;
		}
		final var result = read(source, kinds, progress);
		if (result.isEmpty()) {
			return true;
		}
		if (!single) {
			result.get().notices().stream()
				.filter(notice -> selection.includes(notice.key()))
				.forEach(notice -> {
					saveLine(run, notice, progress);
					progress.found.add(notice.key());
				});
		}
		return migrateItems(run, result.get().candidates(), selection, single, context, progress);
	}

	/**
	 * Migrates the selected items of a source.
	 *
	 * @param  single true for the run of a single item, whose row is kept as its result
	 * @return        false when the run was asked to stop
	 */
	private boolean migrateItems(final MigrationRunEntity run, final List<Candidate> candidates, final ItemSelection selection, final boolean single, final RunContext context,
		final Progress progress) {
		final var selected = candidates.stream().filter(candidate -> selection.includes(candidate.key())).toList();
		for (final var candidate : selected) {
			if (context.stopRequested()) {
				progress.stop();
				return false;
			}
			final var result = migrator.process(candidate, context);
			context.recordOutcome(candidate.key(), result.outcome());
			if (result.outcome() == Outcome.EJ_KORD) {
				progress.limitReached = true;
			}
			final var line = saveLine(run, candidate, result, progress);
			progress.found.add(candidate.key());
			if (single) {
				progress.singleResult = line;
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

	private RunItemEntity saveLine(final MigrationRunEntity run, final Candidate candidate, final ItemResult result, final RunProgress progress) {
		final var line = records.newLine(run, candidate.key());
		line.setOutcome(result.outcome());
		line.setRules(Texts.truncate(String.join(",", result.rules()), MAX_RULES_LENGTH, null));
		line.setDetail(ReportNotes.reportText(result.detail()));
		line.setPlaceName(candidate.placeName());
		line.setPlaceNumber(candidate.placeNumber());
		line.setErrandNumber(result.errandNumber());
		records.save(line, progress);
		return line;
	}

	private void saveLine(final MigrationRunEntity run, final Notice notice, final RunProgress progress) {
		final var line = records.newLine(run, notice.key());
		line.setOutcome(notice.outcome());
		line.setRules(notice.rule());
		line.setDetail(ReportNotes.reportText(notice.detail()));
		line.setPlaceName(notice.placeName());
		records.save(line, progress);
	}

	/**
	 * How far a run has come, whether it reached its limit, the items it found in the sources and, for a single item, the
	 * item's result.
	 */
	private static final class Progress extends RunProgress {

		private final Set<ItemKey> found = new HashSet<>();
		private boolean limitReached;
		private RunItemEntity singleResult;
	}
}
