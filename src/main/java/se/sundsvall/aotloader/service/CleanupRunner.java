package se.sundsvall.aotloader.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.integration.db.MigrationItemRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationItemEntity;
import se.sundsvall.aotloader.integration.db.model.MigrationRunEntity;
import se.sundsvall.aotloader.integration.db.model.RunItemEntity;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.ItemSelection;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.RunStatus;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.support.ErrorTexts;

/**
 * Runs a cleanup: removes what the migration wrote to the target systems for the items of the status table in a scope,
 * and the items, the inspection visits first and the permits last (section 10.3). An item that belongs to another
 * municipality is left as it is. Nothing an item does stops the cleanup; a cleanup that cannot go on is marked FAILED
 * with the reason, and the lock is always released by the caller.
 */
@Component
public class CleanupRunner {

	private static final Logger LOG = LoggerFactory.getLogger(CleanupRunner.class);
	private static final Comparator<MigrationItemEntity> ORDER = Comparator.comparing(MigrationItemEntity::getKind, Comparator.reverseOrder())
		.thenComparing(MigrationItemEntity::getSource)
		.thenComparing(MigrationItemEntity::getSourceId);

	private final ItemCleaner cleaner;
	private final TargetGuard targetGuard;
	private final MigrationItemRepository itemRepository;
	private final RunRecords records;
	private final Map<String, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();

	CleanupRunner(final ItemCleaner cleaner, final TargetGuard targetGuard, final MigrationItemRepository itemRepository, final RunRecords records) {
		this.cleaner = cleaner;
		this.targetGuard = targetGuard;
		this.itemRepository = itemRepository;
		this.records = records;
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
	 * Asks a cleanup to stop before its next item.
	 *
	 * @return true when the cleanup was running
	 */
	public boolean requestStop(final String runId) {
		return Optional.ofNullable(stopFlags.get(runId)).map(flag -> {
			flag.set(true);
			return true;
		}).orElse(false);
	}

	/**
	 * Cleans up the items of the scope, the items the cleanup is narrowed to, or the single item when one is given. Never
	 * throws, except for an {@link Error}, after which the cleanup is still saved as FAILED.
	 *
	 * @param  run    the cleanup, saved as RUNNING, with the items it is narrowed to, if any
	 * @param  scope  the sources and their kinds
	 * @param  single the one item to clean up, empty for every item of the scope or of the cleanup's selection
	 * @return        the row of the report of the single item, whatever its outcome, also when it could not be saved; empty
	 *                for a full cleanup, and when the item is not in the status table or the cleanup stopped or failed
	 *                before
	 *                it
	 */
	public Optional<RunItemEntity> execute(final MigrationRunEntity run, final Map<Source, Set<Kind>> scope, final Optional<ItemKey> single) {
		final var stop = stopFlags.computeIfAbsent(run.getId(), _ -> new AtomicBoolean());
		final var progress = new Progress();
		try {
			final var selection = single.map(key -> ItemSelection.of(List.of(key))).orElseGet(() -> ItemSelection.parse(run.getItemKeys()));
			targetGuard.check().ifPresentOrElse(
				wrongTarget -> progress.fail("Rensningen startade inte. " + wrongTarget),
				() -> clean(run, items(run.getMunicipalityId(), scope, selection, single.isEmpty(), progress), single.isPresent(), stop, progress));
			if (single.isPresent() && progress.singleResult == null && progress.status() == RunStatus.COMPLETED) {
				progress.note("Ärendet %s finns inte i statustabellen".formatted(single.get()));
			}
		} catch (final RuntimeException e) {
			LOG.error("Cleanup {} failed: {}", run.getId(), e.getClass().getSimpleName(), ErrorTexts.forLog(e));
			progress.fail("Rensningen avbröts av ett oväntat fel: " + ErrorTexts.describe(e));
		} catch (final Error e) {
			progress.fail("Rensningen avbröts av ett oväntat fel: " + e.getClass().getSimpleName());
			throw e;
		} finally {
			stopFlags.remove(run.getId());
			records.finish(run, progress);
		}
		return Optional.ofNullable(progress.singleResult);
	}

	/**
	 * @param  noteMissing true to tell, on the progress, the selected items the status table does not have
	 * @return             the selected items of the status table in the scope and the municipality, in the order to clean
	 *                     them up; an item that never reached a target system has no municipality and is in every one
	 */
	private List<MigrationItemEntity> items(final String municipalityId, final Map<Source, Set<Kind>> scope, final ItemSelection selection, final boolean noteMissing,
		final Progress progress) {
		final var items = itemRepository.findAllByOrderBySourceAscKindAscSourceIdAsc().stream()
			.filter(item -> scope.getOrDefault(item.getSource(), Set.of()).contains(item.getKind()))
			.filter(item -> item.getMunicipalityId() == null || municipalityId.equals(item.getMunicipalityId()))
			.filter(item -> selection.includes(keyOf(item)))
			.sorted(ORDER)
			.toList();
		final var missing = selection.missing(items.stream().map(CleanupRunner::keyOf).collect(Collectors.toSet()));
		if (noteMissing && !missing.isEmpty()) {
			progress.note("%d av ärendena i urvalet finns inte i statustabellen: %s".formatted(missing.size(), ItemSelection.listText(missing)));
		}
		return items;
	}

	private static ItemKey keyOf(final MigrationItemEntity item) {
		return new ItemKey(item.getSource(), item.getKind(), item.getSourceId());
	}

	private void clean(final MigrationRunEntity run, final List<MigrationItemEntity> items, final boolean single, final AtomicBoolean stop, final Progress progress) {
		var cleaned = 0;
		for (final var item : items) {
			if (stop.get()) {
				progress.stop();
				return;
			}
			if (run.getItemLimit() != null && cleaned >= run.getItemLimit()) {
				progress.note("Gränsen på %d ärenden nåddes; resten tas i nästa rensning".formatted(run.getItemLimit()));
				return;
			}
			final var result = cleaner.clean(item, run.getMunicipalityId(), run.isDryRun());
			cleaned++;
			final var line = saveLine(run, item, result, progress);
			if (single) {
				progress.singleResult = line;
			}
		}
	}

	private RunItemEntity saveLine(final MigrationRunEntity run, final MigrationItemEntity item, final ItemResult result, final RunProgress progress) {
		final var line = records.newLine(run, keyOf(item));
		line.setOutcome(result.outcome());
		// A cleanup applies no review rules, so the column is empty as for an item without hits
		line.setRules(String.join(",", result.rules()));
		line.setDetail(ReportNotes.reportText(result.detail()));
		line.setPlaceName(item.getPlaceName());
		line.setPlaceNumber(item.getPlaceNumber());
		line.setErrandNumber(result.errandNumber());
		records.save(line, progress);
		return line;
	}

	/**
	 * How far a cleanup has come, and for a single item, the item's result.
	 */
	private static final class Progress extends RunProgress {

		private RunItemEntity singleResult;
	}
}
