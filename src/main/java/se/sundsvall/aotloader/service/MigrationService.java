package se.sundsvall.aotloader.service;

import java.time.Clock;
import java.time.LocalDate;
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
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import se.sundsvall.aotloader.api.model.MigrationItem;
import se.sundsvall.aotloader.api.model.ReportLine;
import se.sundsvall.aotloader.api.model.ReviewException;
import se.sundsvall.aotloader.api.model.ReviewExceptionRequest;
import se.sundsvall.aotloader.api.model.Run;
import se.sundsvall.aotloader.api.model.RunRequest;
import se.sundsvall.aotloader.integration.db.MigrationItemRepository;
import se.sundsvall.aotloader.integration.db.MigrationRunRepository;
import se.sundsvall.aotloader.integration.db.ReviewExceptionRepository;
import se.sundsvall.aotloader.integration.db.RunItemRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationItemEntity;
import se.sundsvall.aotloader.integration.db.model.MigrationRunEntity;
import se.sundsvall.aotloader.integration.db.model.ReviewExceptionEntity;
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
 * Starts runs, one at a time, and answers about runs, reports, the status table and the exception list. Starting a run
 * again never migrates an item twice: an item already migrated is left as it is, and an item that stopped half way
 * continues from where it stopped.
 */
@Service
public class MigrationService {

	private final MigrationRunner runner;
	private final RunLock lock;
	private final ExecutorService executor;
	private final MigrationRunRepository runRepository;
	private final RunItemRepository runItemRepository;
	private final MigrationItemRepository itemRepository;
	private final ReviewExceptionRepository exceptionRepository;
	private final Clock clock;

	MigrationService(final MigrationRunner runner, final RunLock lock, @Qualifier(MIGRATION_EXECUTOR) final ExecutorService executor, final MigrationRunRepository runRepository, final RunItemRepository runItemRepository,
		final MigrationItemRepository itemRepository, final ReviewExceptionRepository exceptionRepository, final Clock clock) {
		this.runner = runner;
		this.lock = lock;
		this.executor = executor;
		this.runRepository = runRepository;
		this.runItemRepository = runItemRepository;
		this.itemRepository = itemRepository;
		this.exceptionRepository = exceptionRepository;
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
		final var run = newRun(municipalityId, request.isDryRun(), scope, null, request.limit());
		acquire(run.getId());
		try {
			runRepository.save(run);
			executor.execute(() -> {
				try {
					runner.execute(run, scope, Optional.empty());
				} finally {
					lock.release(run.getId());
				}
			});
		} catch (final RuntimeException e) {
			lock.release(run.getId());
			throw e;
		}
		return toRun(run, Map.of());
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
		if (!runner.supportedKinds().getOrDefault(key.source(), Set.of()).contains(key.kind())) {
			throw Problem.valueOf(BAD_REQUEST, "%s har inga ärenden av sorten %s".formatted(key.source().getDisplayName(), key.kind()));
		}
		final var scope = Map.of(key.source(), Set.of(key.kind()));
		final var run = newRun(municipalityId, dryRun, scope, key.toString(), null);
		acquire(run.getId());
		try {
			runRepository.save(run);
			if (runner.execute(run, scope, Optional.of(key)).isEmpty()) {
				final var message = Optional.ofNullable(run.getMessage()).orElse("Ärendet %s hittades inte".formatted(key));
				throw Problem.valueOf(run.getStatus() == RunStatus.COMPLETED ? NOT_FOUND : UNPROCESSABLE_CONTENT, message);
			}
			return runItemRepository.findByRunIdOrderById(run.getId()).stream()
				.filter(line -> key.equals(new ItemKey(line.getSource(), line.getKind(), line.getSourceId())))
				.reduce((first, second) -> second)
				.map(MigrationService::toReportLine)
				.orElseThrow(() -> Problem.valueOf(NOT_FOUND, "Ärendet %s hittades inte".formatted(key)));
		} finally {
			lock.release(run.getId());
		}
	}

	/**
	 * Asks a running run to stop before its next item.
	 *
	 * @return the run
	 */
	public Run stopRun(final String municipalityId, final String runId) {
		final var run = findRun(municipalityId, runId);
		if (run.getStatus() != RunStatus.RUNNING || !runner.requestStop(runId)) {
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
		return runItemRepository.findByRunIdOrderById(runId).stream()
			.filter(line -> outcomes == null || outcomes.isEmpty() || outcomes.contains(line.getOutcome()))
			.map(MigrationService::toReportLine)
			.toList();
	}

	/**
	 * @return the rows of the status table, filtered on what is given
	 */
	public List<MigrationItem> getItems(final Source source, final Kind kind, final ItemStatus status) {
		return itemRepository.findAllByOrderBySourceAscKindAscSourceIdAsc().stream()
			.filter(item -> source == null || source == item.getSource())
			.filter(item -> kind == null || kind == item.getKind())
			.filter(item -> status == null || status == item.getStatus())
			.map(MigrationService::toMigrationItem)
			.toList();
	}

	public MigrationItem getItem(final ItemKey key) {
		return itemRepository.findBySourceAndKindAndSourceId(key.source(), key.kind(), key.sourceId())
			.map(MigrationService::toMigrationItem)
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, "Ärendet %s finns inte i statustabellen".formatted(key)));
	}

	public List<ReviewException> getExceptions() {
		return exceptionRepository.findAllByOrderBySourceAscKindAscSourceIdAsc().stream().map(MigrationService::toReviewException).toList();
	}

	/**
	 * Adds the item to the exception list, or changes what it says.
	 */
	public ReviewException putException(final ItemKey key, final ReviewExceptionRequest request) {
		final var entity = exceptionRepository.findBySourceAndKindAndSourceId(key.source(), key.kind(), key.sourceId()).orElseGet(() -> {
			final var created = new ReviewExceptionEntity();
			created.setSource(key.source());
			created.setKind(key.kind());
			created.setSourceId(key.sourceId());
			created.setCreated(OffsetDateTime.now(clock));
			return created;
		});
		entity.setOutcome(request.outcome());
		entity.setDecidedBy(request.decidedBy().strip());
		entity.setDecidedAt(Optional.ofNullable(request.decidedAt()).orElse(LocalDate.now(clock)));
		entity.setComment(request.comment());
		return toReviewException(exceptionRepository.save(entity));
	}

	public void deleteException(final ItemKey key) {
		final var entity = exceptionRepository.findBySourceAndKindAndSourceId(key.source(), key.kind(), key.sourceId())
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, "Ärendet %s finns inte i undantagslistan".formatted(key)));
		exceptionRepository.delete(entity);
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

	private void acquire(final String runId) {
		if (!lock.tryAcquire(runId)) {
			throw Problem.valueOf(CONFLICT, "En körning pågår redan (%s). Vänta tills den är klar, eller stoppa den.".formatted(lock.holder().orElse("okänd")));
		}
	}

	private MigrationRunEntity newRun(final String municipalityId, final boolean dryRun, final Map<Source, Set<Kind>> scope, final String itemKey, final Integer limit) {
		final var run = new MigrationRunEntity();
		run.setId(UUID.randomUUID().toString());
		run.setMunicipalityId(municipalityId);
		run.setDryRun(dryRun);
		run.setSources(scope.keySet().stream().map(Source::name).collect(Collectors.joining(",")));
		run.setKinds(scope.values().stream().flatMap(Set::stream).distinct().sorted().map(Kind::name).collect(Collectors.joining(",")));
		run.setItemKey(itemKey);
		run.setItemLimit(limit);
		run.setStatus(RunStatus.RUNNING);
		run.setStarted(OffsetDateTime.now(clock));
		return run;
	}

	private MigrationRunEntity findRun(final String municipalityId, final String runId) {
		return runRepository.findById(runId)
			.filter(run -> municipalityId.equals(run.getMunicipalityId()))
			.orElseThrow(() -> Problem.valueOf(NOT_FOUND, "Körningen %s finns inte".formatted(runId)));
	}

	private Map<Outcome, Long> counts(final String runId) {
		return OutcomeCounts.of(runItemRepository.findByRunIdOrderById(runId));
	}

	private static Run toRun(final MigrationRunEntity run, final Map<Outcome, Long> counts) {
		return new Run(run.getId(), run.getMunicipalityId(), run.isDryRun(), split(run.getSources()), split(run.getKinds()), run.getItemKey(), run.getItemLimit(), run.getStatus(), run.getMessage(), run.getStarted(),
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

	private static ReviewException toReviewException(final ReviewExceptionEntity entity) {
		return new ReviewException(entity.getSource(), entity.getKind(), entity.getSourceId(), entity.getOutcome(), entity.getDecidedBy(), entity.getDecidedAt(), entity.getComment());
	}
}
