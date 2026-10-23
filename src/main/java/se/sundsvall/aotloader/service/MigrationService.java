package se.sundsvall.aotloader.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import se.sundsvall.aotloader.api.model.CleanupRequest;
import se.sundsvall.aotloader.api.model.MigrationItem;
import se.sundsvall.aotloader.api.model.ReportLine;
import se.sundsvall.aotloader.api.model.Run;
import se.sundsvall.aotloader.api.model.RunRequest;
import se.sundsvall.aotloader.integration.db.MigrationItemRepository;
import se.sundsvall.aotloader.integration.db.MigrationRunRepository;
import se.sundsvall.aotloader.integration.db.RunItemRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationItemEntity;
import se.sundsvall.aotloader.integration.db.model.MigrationRunEntity;
import se.sundsvall.aotloader.integration.db.model.RunItemEntity;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.ItemStatus;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.RunStatus;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.dept44.problem.Problem;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;
import static se.sundsvall.aotloader.configuration.ExecutorConfiguration.MIGRATION_EXECUTOR;

/**
 * Starts runs and cleanups, one at a time, and answers about runs, reports and the status table.
 * Starting a run again never migrates an item twice: an item already migrated is left as it is, and an item that
 * stopped half way continues from where it stopped.
 */
@Service
public class MigrationService {

	private final MigrationRunner runner;
	private final CleanupRunner cleanupRunner;
	private final RunLock lock;
	private final ExecutorService executor;
	private final MigrationRunRepository runRepository;
	private final RunItemRepository runItemRepository;
	private final MigrationItemRepository itemRepository;
	private final Clock clock;

	MigrationService(final MigrationRunner runner, final CleanupRunner cleanupRunner, final RunLock lock, @Qualifier(MIGRATION_EXECUTOR) final ExecutorService executor, final MigrationRunRepository runRepository,
		final RunItemRepository runItemRepository, final MigrationItemRepository itemRepository, final Clock clock) {
		this.runner = runner;
		this.cleanupRunner = cleanupRunner;
		this.lock = lock;
		this.executor = executor;
		this.runRepository = runRepository;
		this.runItemRepository = runItemRepository;
		this.itemRepository = itemRepository;
		this.clock = clock;
	}

	/**
	 * Starts a run in the background.
	 *
	 * @return                                              the run, RUNNING
	 * @throws se.sundsvall.dept44.problem.ThrowableProblem 409 when another run is running, 400 when the request selects
	 *                                                      nothing to migrate
	 */
	public Run startRun(final String municipalityId, final RunRequest request) {
		final var scope = scope(request.sources(), request.kinds());
		final var run = newRun(municipalityId, request.dryRunOrDefault(), false, scope, null, request.limit());
		return startInBackground(run, runner::register, runner::forget, () -> runner.execute(run, scope, Optional.empty()));
	}

	/**
	 * Migrates a single item, or tries it in a dry run, and waits for it.
	 *
	 * @return                                              the row of the report for the item
	 * @throws se.sundsvall.dept44.problem.ThrowableProblem 400 when the source has no items of the kind, 409 when a run is
	 *                                                      running, 404 when the source has no such item, 422 when the
	 *                                                      run did not start, failed or was stopped
	 */
	public ReportLine migrateItem(final String municipalityId, final ItemKey key, final boolean dryRun) {
		final var scope = singleScope(key);
		final var run = newRun(municipalityId, dryRun, false, scope, key.toString(), null);
		return runSingle(run, () -> runner.execute(run, scope, Optional.of(key)), "Ärendet %s hittades inte".formatted(key));
	}

	/**
	 * Starts a cleanup in the background, which removes what the migration wrote to the target systems for the items of
	 * the status table in the scope, and the items (section 10.3).
	 *
	 * @return                                              the cleanup, RUNNING
	 * @throws se.sundsvall.dept44.problem.ThrowableProblem 409 when another run is running, 400 when the request selects
	 *                                                      nothing
	 */
	public Run startCleanup(final String municipalityId, final CleanupRequest request) {
		final var scope = scope(request.sources(), request.kinds());
		final var run = newRun(municipalityId, request.dryRunOrDefault(), true, scope, null, request.limit());
		return startInBackground(run, cleanupRunner::register, cleanupRunner::forget, () -> cleanupRunner.execute(run, scope, Optional.empty()));
	}

	/**
	 * Cleans up a single item, or tries it in a dry run, and waits for it.
	 *
	 * @return                                              the row of the report for the item
	 * @throws se.sundsvall.dept44.problem.ThrowableProblem 400 when the source has no items of the kind, 409 when a run is
	 *                                                      running, 404 when the status table has no such item, 422 when
	 *                                                      the cleanup did not start or failed
	 */
	public ReportLine cleanupItem(final String municipalityId, final ItemKey key, final boolean dryRun) {
		final var scope = singleScope(key);
		final var run = newRun(municipalityId, dryRun, true, scope, key.toString(), null);
		return runSingle(run, () -> cleanupRunner.execute(run, scope, Optional.of(key)), "Ärendet %s finns inte i statustabellen".formatted(key));
	}

	/**
	 * Asks a running run or cleanup to stop before its next item.
	 *
	 * @return the run
	 */
	public Run stopRun(final String municipalityId, final String runId) {
		final var run = findRun(municipalityId, runId);
		if (run.getStatus() != RunStatus.RUNNING || !(run.isCleanup() ? cleanupRunner.requestStop(runId) : runner.requestStop(runId))) {
			throw Problem.valueOf(CONFLICT, "Körningen %s pågår inte".formatted(runId));
		}
		return toRun(run, counts(runId));
	}

	public List<Run> getRuns(final String municipalityId) {
		return runRepository.findByMunicipalityIdOrderByStartedDesc(municipalityId).stream()
			.map(run -> toRun(run, counts(run.getId())))
			.toList();
	}

	public Run getRun(final String municipalityId, final String runId) {
		return toRun(findRun(municipalityId, runId), counts(runId));
	}

	/**
	 * @param  outcomes the outcomes to list, every outcome when empty
	 * @return          the rows of the report of the run
	 */
	public List<ReportLine> getReport(final String municipalityId, final String runId, final Collection<Outcome> outcomes) {
		findRun(municipalityId, runId);
		final var lines = outcomes == null || outcomes.isEmpty() ? runItemRepository.findByRunIdOrderById(runId) : runItemRepository.findByRunIdAndOutcomeInOrderById(runId, outcomes);
		return lines.stream().map(MigrationService::toReportLine).toList();
	}

	/**
	 * @return the rows of the status table of the municipality, filtered on what is given; an item that never reached a
	 *         target system has no municipality and is in every one
	 */
	public List<MigrationItem> getItems(final String municipalityId, final Source source, final Kind kind, final ItemStatus status) {
		return itemRepository.findAllByOrderBySourceAscKindAscSourceIdAsc().stream()
			.filter(item -> isOf(item, municipalityId))
			.filter(item -> source == null || source == item.getSource())
			.filter(item -> kind == null || kind == item.getKind())
			.filter(item -> status == null || status == item.getStatus())
			.map(MigrationService::toMigrationItem)
			.toList();
	}

	/**
	 * @return the row of the status table of the item, when it is of the municipality, as above
	 */
	public MigrationItem getItem(final String municipalityId, final ItemKey key) {
		return itemRepository.findBySourceAndKindAndSourceId(key.source(), key.kind(), key.sourceId())
			.filter(item -> isOf(item, municipalityId))
			.map(MigrationService::toMigrationItem)
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, "Ärendet %s finns inte i statustabellen".formatted(key)));
	}

	/**
	 * The sources and kinds of a run: those asked for that exist, all when nothing is asked for.
	 */
	Map<Source, Set<Kind>> scope(final List<Source> sources, final List<Kind> kinds) {
		final var scope = new EnumMap<Source, Set<Kind>>(Source.class);
		final var wantedSources = sources == null || sources.isEmpty() ? EnumSet.allOf(Source.class) : EnumSet.copyOf(sources);
		final var wantedKinds = kinds == null || kinds.isEmpty() ? EnumSet.allOf(Kind.class) : EnumSet.copyOf(kinds);
		runner.supportedKinds().forEach((source, supported) -> {
			if (wantedSources.contains(source)) {
				final var selected = EnumSet.noneOf(Kind.class);
				supported.stream().filter(wantedKinds::contains).forEach(selected::add);
				if (!selected.isEmpty()) {
					scope.put(source, selected);
				}
			}
		});
		if (scope.isEmpty()) {
			throw Problem.valueOf(BAD_REQUEST, "Urvalet innehåller inga ärenden att migrera");
		}
		return scope;
	}

	/**
	 * @return                                              the scope of the single item
	 * @throws se.sundsvall.dept44.problem.ThrowableProblem 400 when the source has no items of the kind
	 */
	private Map<Source, Set<Kind>> singleScope(final ItemKey key) {
		if (!runner.supportedKinds().getOrDefault(key.source(), Set.of()).contains(key.kind())) {
			throw Problem.valueOf(BAD_REQUEST, "%s har inga ärenden av sorten %s".formatted(key.source().getDisplayName(), key.kind()));
		}
		return Map.of(key.source(), Set.of(key.kind()));
	}

	/**
	 * Saves the run and starts the work in the background, under the lock, which the work releases when it is done.
	 *
	 * @param  register makes the stop flag of the run, before the work is handed to its thread
	 * @param  forget   removes the stop flag when the work could not be handed over
	 * @return          the run, RUNNING
	 */
	private Run startInBackground(final MigrationRunEntity run, final Consumer<String> register, final Consumer<String> forget, final Runnable work) {
		acquire(run.getId());
		try {
			runRepository.save(run);
			// Taken before the run starts, since the runner changes the entity in its own thread
			final var started = toRun(run, Map.of());
			register.accept(run.getId());
			handOver(run.getId(), forget, work);
			return started;
		} catch (final RuntimeException e) {
			lock.release(run.getId());
			throw e;
		}
	}

	/**
	 * Hands the work to its thread, which releases the lock when the work is done.
	 *
	 * @param forget removes the stop flag of the run when the work could not be handed over
	 */
	private void handOver(final String runId, final Consumer<String> forget, final Runnable work) {
		try {
			executor.execute(() -> runAndRelease(runId, work));
		} catch (final RuntimeException e) {
			forget.accept(runId);
			throw e;
		}
	}

	private void runAndRelease(final String runId, final Runnable work) {
		try {
			work.run();
		} finally {
			lock.release(runId);
		}
	}

	/**
	 * Saves the run of a single item and does the work under the lock, waiting for it.
	 *
	 * @param  notFound the answer when the work finds no item
	 * @return          the row of the report for the item
	 */
	private ReportLine runSingle(final MigrationRunEntity run, final Supplier<Optional<RunItemEntity>> work, final String notFound) {
		acquire(run.getId());
		try {
			runRepository.save(run);
			// The row as the run made it, also when it could not be saved
			return work.get()
				.map(MigrationService::toReportLine)
				.orElseThrow(() -> Problem.valueOf(run.getStatus() == RunStatus.COMPLETED ? NOT_FOUND : UNPROCESSABLE_CONTENT, Optional.ofNullable(run.getMessage()).orElse(notFound)));
		} finally {
			lock.release(run.getId());
		}
	}

	private void acquire(final String runId) {
		if (!lock.tryAcquire(runId)) {
			throw Problem.valueOf(CONFLICT, "En körning pågår redan (%s). Vänta tills den är klar, eller stoppa den.".formatted(lock.holder().orElse("okänd")));
		}
	}

	private MigrationRunEntity newRun(final String municipalityId, final boolean dryRun, final boolean cleanup, final Map<Source, Set<Kind>> scope, final String itemKey, final Integer limit) {
		final var run = new MigrationRunEntity();
		run.setId(UUID.randomUUID().toString());
		run.setMunicipalityId(municipalityId);
		run.setDryRun(dryRun);
		run.setCleanup(cleanup);
		run.setSources(scope.keySet().stream().map(Source::name).collect(Collectors.joining(",")));
		run.setKinds(scope.values().stream().flatMap(Set::stream).distinct().sorted().map(Kind::name).collect(Collectors.joining(",")));
		run.setItemKey(itemKey);
		run.setItemLimit(limit);
		run.setStatus(RunStatus.RUNNING);
		run.setStarted(OffsetDateTime.now(clock));
		return run;
	}

	private static boolean isOf(final MigrationItemEntity item, final String municipalityId) {
		return item.getMunicipalityId() == null || item.getMunicipalityId().equals(municipalityId);
	}

	private MigrationRunEntity findRun(final String municipalityId, final String runId) {
		return runRepository.findById(runId)
			.filter(run -> municipalityId.equals(run.getMunicipalityId()))
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, "Körningen %s finns inte".formatted(runId)));
	}

	private Map<Outcome, Long> counts(final String runId) {
		return OutcomeCounts.of(runItemRepository, runId);
	}

	private static Run toRun(final MigrationRunEntity run, final Map<Outcome, Long> counts) {
		return new Run(run.getId(), run.getMunicipalityId(), run.isDryRun(), run.isCleanup(), split(run.getSources()), split(run.getKinds()), run.getItemKey(), run.getItemLimit(), run.getStatus(), run.getMessage(),
			run.getStarted(),
			run.getFinished(), counts);
	}

	private static List<String> split(final String value) {
		return value == null || value.isBlank() ? List.of() : new ArrayList<>(List.of(value.split(",")));
	}

	static ReportLine toReportLine(final RunItemEntity line) {
		return new ReportLine(line.getSource(), line.getKind(), line.getSourceId(), line.getPlaceNumber(), line.getPlaceName(), line.getOutcome(), line.getRules(), line.getDetail(), line.getErrandNumber());
	}

	private static MigrationItem toMigrationItem(final MigrationItemEntity item) {
		return new MigrationItem(item.getSource(), item.getKind(), item.getSourceId(), item.getErrandType(), item.getStatus(), item.getFailedStep(), item.getReason(), item.getPlaceNumber(), item.getPlaceName(), item.getMunicipalityId(), item.getErrandId(),
			item.getErrandNumber(), item.getAssetId(), item.getPartyAssetsId(), item.getRelationId(), item.getLastRunId(), item.getModified());
	}
}
