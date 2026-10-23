package se.sundsvall.aotloader.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.aotloader.api.model.CleanupRequest;
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
import se.sundsvall.dept44.problem.ThrowableProblem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;
import static se.sundsvall.aotloader.service.model.Kind.ANMALAN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYN;

@ExtendWith(MockitoExtension.class)
class MigrationServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T08:00:00Z"), ZoneId.of("Europe/Stockholm"));

	@Mock
	private MigrationRunner runner;

	@Mock
	private CleanupRunner cleanupRunner;

	@Mock
	private ExecutorService executor;

	@Mock
	private MigrationRunRepository runRepository;

	@Mock
	private RunItemRepository runItemRepository;

	@Mock
	private MigrationItemRepository itemRepository;

	private final RunLock lock = new RunLock();
	private MigrationService service;

	@BeforeEach
	void setUp() {
		lenient().when(runner.supportedKinds()).thenReturn(Map.of(Source.ALKT, Set.of(TILLSTAND, TILLSYN), Source.ALKOL, Set.of(Kind.values())));
		service = new MigrationService(runner, cleanupRunner, lock, executor, runRepository, runItemRepository, itemRepository, CLOCK);
	}

	@Test
	void startRunIsADryRunUnlessAskedOtherwise() {
		final var run = service.startRun(MUNICIPALITY_ID, new RunRequest(null, null, null, null));

		assertThat(run.dryRun()).isTrue();
		assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
		assertThat(run.sources()).containsExactly("ALKT", "ALKOL");
		assertThat(run.kinds()).containsExactly("TILLSTAND", "ANMALAN", "TILLSYN", "TILLSYNSBESOK");
		assertThat(lock.holder()).contains(run.id());
		final var task = ArgumentCaptor.forClass(Runnable.class);
		verify(executor).execute(task.capture());

		task.getValue().run();

		verify(runner).execute(any(MigrationRunEntity.class), eq(Map.of(Source.ALKT, Set.of(TILLSTAND, TILLSYN), Source.ALKOL, Set.of(Kind.values()))), eq(Optional.empty()));
		assertThat(lock.holder()).isEmpty();
	}

	@Test
	void theAnswerIsTheRunAsItStartedEvenWhenItFinishesFirst() {
		doAnswer(invocation -> {
			invocation.<Runnable>getArgument(0).run();
			return null;
		}).when(executor).execute(any());
		doAnswer(invocation -> {
			invocation.<MigrationRunEntity>getArgument(0).setStatus(RunStatus.COMPLETED);
			return null;
		}).when(runner).execute(any(), any(), any());

		final var run = service.startRun(MUNICIPALITY_ID, new RunRequest(null, null, null, null));

		assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
		assertThat(lock.holder()).isEmpty();
	}

	@Test
	void onlyOneRunAtATime() {
		service.startRun(MUNICIPALITY_ID, new RunRequest(false, List.of(Source.ALKT), List.of(TILLSTAND), 10));

		assertThatThrownBy(() -> service.startRun(MUNICIPALITY_ID, new RunRequest(true, null, null, null)))
			.isInstanceOfSatisfying(ThrowableProblem.class, problem -> assertThat(problem.getStatus()).isEqualTo(CONFLICT));
		assertThatThrownBy(() -> service.migrateItem(MUNICIPALITY_ID, new ItemKey(Source.ALKT, TILLSTAND, "1"), true))
			.isInstanceOfSatisfying(ThrowableProblem.class, problem -> assertThat(problem.getStatus()).isEqualTo(CONFLICT));
	}

	@Test
	void theLockIsReleasedWhenTheRunCannotBeStarted() {
		doThrow(new RejectedExecutionException()).when(executor).execute(any());
		final var request = new RunRequest(true, null, null, null);

		assertThatThrownBy(() -> service.startRun(MUNICIPALITY_ID, request)).isInstanceOf(RejectedExecutionException.class);
		assertThat(lock.holder()).isEmpty();
	}

	@Test
	void aSelectionWithNothingToMigrate() {
		assertThatThrownBy(() -> service.startRun(MUNICIPALITY_ID, new RunRequest(true, List.of(Source.ALKT), List.of(ANMALAN), null)))
			.isInstanceOfSatisfying(ThrowableProblem.class, problem -> {
				assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST);
				assertThat(problem.getDetail()).isEqualTo("Urvalet innehåller inga ärenden att migrera");
			});
		assertThat(lock.holder()).isEmpty();
	}

	@Test
	void migrateItem() {
		final var key = new ItemKey(Source.ALKT, TILLSTAND, "1");
		final var line = new RunItemEntity();
		line.setSource(Source.ALKT);
		line.setKind(TILLSTAND);
		line.setSourceId("1");
		line.setOutcome(Outcome.MIGRERAD);
		line.setErrandNumber("ALKT-1");
		when(runner.execute(any(), eq(Map.of(Source.ALKT, Set.of(TILLSTAND))), eq(Optional.of(key)))).thenReturn(Optional.of(line));

		final var result = service.migrateItem(MUNICIPALITY_ID, key, false);

		assertThat(result.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(result.errandNumber()).isEqualTo("ALKT-1");
		final var run = ArgumentCaptor.forClass(MigrationRunEntity.class);
		verify(runRepository).save(run.capture());
		assertThat(run.getValue().isDryRun()).isFalse();
		assertThat(run.getValue().getItemKey()).isEqualTo("ALKT/TILLSTAND/1");
		assertThat(lock.holder()).isEmpty();
	}

	@Test
	void migrateItemThatIsNotFoundOrCannotRun() {
		final var key = new ItemKey(Source.ALKT, TILLSTAND, "1");
		finishAs(RunStatus.COMPLETED, null);

		assertThatThrownBy(() -> service.migrateItem(MUNICIPALITY_ID, key, true))
			.isInstanceOfSatisfying(ThrowableProblem.class, problem -> assertThat(problem.getStatus()).isEqualTo(NOT_FOUND));

		finishAs(RunStatus.STOPPED, "Körningen stoppades på begäran");

		assertThatThrownBy(() -> service.migrateItem(MUNICIPALITY_ID, key, true))
			.isInstanceOfSatisfying(ThrowableProblem.class, problem -> assertThat(problem.getStatus()).isEqualTo(UNPROCESSABLE_CONTENT));

		finishAs(RunStatus.FAILED, "Körningen startade inte");

		assertThatThrownBy(() -> service.migrateItem(MUNICIPALITY_ID, key, false))
			.isInstanceOfSatisfying(ThrowableProblem.class, problem -> {
				assertThat(problem.getStatus()).isEqualTo(UNPROCESSABLE_CONTENT);
				assertThat(problem.getDetail()).isEqualTo("Körningen startade inte");
			});
		assertThatThrownBy(() -> service.migrateItem(MUNICIPALITY_ID, new ItemKey(Source.ALKT, ANMALAN, "1-FOLKOL"), true))
			.isInstanceOfSatisfying(ThrowableProblem.class, problem -> assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST));
		assertThat(lock.holder()).isEmpty();
	}

	@Test
	void runsAndReports() {
		final var run = runEntity(RunStatus.COMPLETED);
		when(runRepository.findByMunicipalityIdOrderByStartedDesc(MUNICIPALITY_ID)).thenReturn(List.of(run));
		when(runRepository.findById("run-id")).thenReturn(Optional.of(run));
		when(runItemRepository.findByRunIdOrderById("run-id")).thenReturn(List.of(line(Outcome.MIGRERAD), line(Outcome.FEL), line(Outcome.FEL)));
		when(runItemRepository.findByRunIdAndOutcomeInOrderById("run-id", List.of(Outcome.FEL))).thenReturn(List.of(line(Outcome.FEL), line(Outcome.FEL)));
		when(runItemRepository.countOutcomes("run-id")).thenReturn(List.of(count(Outcome.FEL, 2), count(Outcome.MIGRERAD, 1)));

		// In the order of the outcomes, whatever order the database gives them in
		assertThat(service.getRuns(MUNICIPALITY_ID)).singleElement().satisfies(r -> assertThat(r.outcomes()).containsExactly(Map.entry(Outcome.MIGRERAD, 1L), Map.entry(Outcome.FEL, 2L)));
		assertThat(service.getRun(MUNICIPALITY_ID, "run-id").sources()).containsExactly("ALKT");
		assertThat(service.getReport(MUNICIPALITY_ID, "run-id", List.of())).hasSize(3);
		assertThat(service.getReport(MUNICIPALITY_ID, "run-id", null)).hasSize(3);
		assertThat(service.getReport(MUNICIPALITY_ID, "run-id", List.of(Outcome.FEL))).hasSize(2);
		assertThatThrownBy(() -> service.getRun("2260", "run-id"))
			.isInstanceOfSatisfying(ThrowableProblem.class, problem -> assertThat(problem.getStatus()).isEqualTo(NOT_FOUND));
	}

	private static RunItemRepository.OutcomeCount count(final Outcome outcome, final long amount) {
		return new RunItemRepository.OutcomeCount() {

			@Override
			public Outcome getOutcome() {
				return outcome;
			}

			@Override
			public long getAmount() {
				return amount;
			}
		};
	}

	@Test
	void startCleanupIsADryRunUnlessAskedOtherwise() {
		final var run = service.startCleanup(MUNICIPALITY_ID, new CleanupRequest(null, List.of(Source.ALKT), null, 5));

		assertThat(run.dryRun()).isTrue();
		assertThat(run.cleanup()).isTrue();
		assertThat(run.limit()).isEqualTo(5);
		assertThat(lock.holder()).contains(run.id());
		final var task = ArgumentCaptor.forClass(Runnable.class);
		verify(executor).execute(task.capture());

		task.getValue().run();

		final var entity = ArgumentCaptor.forClass(MigrationRunEntity.class);
		verify(cleanupRunner).execute(entity.capture(), eq(Map.of(Source.ALKT, Set.of(TILLSTAND, TILLSYN))), eq(Optional.empty()));
		assertThat(entity.getValue().isCleanup()).isTrue();
		verify(runner, never()).execute(any(), any(), any());
		assertThat(lock.holder()).isEmpty();
		// A cleanup holds the same lock as a run
		service.startCleanup(MUNICIPALITY_ID, new CleanupRequest(false, null, null, null));
		assertThatThrownBy(() -> service.startRun(MUNICIPALITY_ID, new RunRequest(true, null, null, null)))
			.isInstanceOfSatisfying(ThrowableProblem.class, problem -> assertThat(problem.getStatus()).isEqualTo(CONFLICT));
	}

	@Test
	void cleanupItem() {
		final var key = new ItemKey(Source.ALKT, TILLSTAND, "1");
		final var line = new RunItemEntity();
		line.setSource(Source.ALKT);
		line.setKind(TILLSTAND);
		line.setSourceId("1");
		line.setOutcome(Outcome.RENSAD);
		when(cleanupRunner.execute(any(), eq(Map.of(Source.ALKT, Set.of(TILLSTAND))), eq(Optional.of(key)))).thenReturn(Optional.of(line));

		assertThat(service.cleanupItem(MUNICIPALITY_ID, key, false).outcome()).isEqualTo(Outcome.RENSAD);

		final var run = ArgumentCaptor.forClass(MigrationRunEntity.class);
		verify(runRepository).save(run.capture());
		assertThat(run.getValue().isCleanup()).isTrue();
		assertThat(run.getValue().isDryRun()).isFalse();
		assertThat(run.getValue().getItemKey()).isEqualTo("ALKT/TILLSTAND/1");
		assertThat(lock.holder()).isEmpty();
	}

	@Test
	void cleanupItemThatIsNotInTheStatusTable() {
		final var key = new ItemKey(Source.ALKT, TILLSTAND, "9");
		doAnswer(invocation -> {
			invocation.<MigrationRunEntity>getArgument(0).setStatus(RunStatus.COMPLETED);
			return Optional.empty();
		}).when(cleanupRunner).execute(any(), any(), any());

		assertThatThrownBy(() -> service.cleanupItem(MUNICIPALITY_ID, key, true))
			.isInstanceOfSatisfying(ThrowableProblem.class, problem -> {
				assertThat(problem.getStatus()).isEqualTo(NOT_FOUND);
				assertThat(problem.getDetail()).isEqualTo("Ärendet ALKT/TILLSTAND/9 finns inte i statustabellen");
			});
		assertThat(lock.holder()).isEmpty();
	}

	@Test
	void stopCleanup() {
		final var cleanup = runEntity(RunStatus.RUNNING);
		cleanup.setCleanup(true);
		when(runRepository.findById("run-id")).thenReturn(Optional.of(cleanup));
		when(cleanupRunner.requestStop("run-id")).thenReturn(true);

		assertThat(service.stopRun(MUNICIPALITY_ID, "run-id").cleanup()).isTrue();
		verify(runner, never()).requestStop(any());
	}

	@Test
	void stopRun() {
		when(runRepository.findById("run-id")).thenReturn(Optional.of(runEntity(RunStatus.RUNNING))).thenReturn(Optional.of(runEntity(RunStatus.COMPLETED)));
		when(runner.requestStop("run-id")).thenReturn(true);

		assertThat(service.stopRun(MUNICIPALITY_ID, "run-id").status()).isEqualTo(RunStatus.RUNNING);
		assertThatThrownBy(() -> service.stopRun(MUNICIPALITY_ID, "run-id"))
			.isInstanceOfSatisfying(ThrowableProblem.class, problem -> assertThat(problem.getStatus()).isEqualTo(CONFLICT));
	}

	@Test
	void items() {
		final var item = new MigrationItemEntity();
		item.setSource(Source.ALKOL);
		item.setKind(ANMALAN);
		item.setSourceId("1-FOLKOL");
		item.setStatus(ItemStatus.KLAR);
		when(itemRepository.findAllByOrderBySourceAscKindAscSourceIdAsc()).thenReturn(List.of(item));
		when(itemRepository.findByKey(new ItemKey(Source.ALKOL, ANMALAN, "1-FOLKOL"))).thenReturn(Optional.of(item));

		assertThat(service.getItems(MUNICIPALITY_ID, null, null, null)).hasSize(1);
		assertThat(service.getItems(MUNICIPALITY_ID, Source.ALKOL, ANMALAN, ItemStatus.KLAR)).hasSize(1);
		assertThat(service.getItems(MUNICIPALITY_ID, Source.ALKT, null, null)).isEmpty();
		assertThat(service.getItems(MUNICIPALITY_ID, null, TILLSTAND, null)).isEmpty();
		assertThat(service.getItems(MUNICIPALITY_ID, null, null, ItemStatus.FEL)).isEmpty();
		assertThat(service.getItem(MUNICIPALITY_ID, new ItemKey(Source.ALKOL, ANMALAN, "1-FOLKOL")).status()).isEqualTo(ItemStatus.KLAR);
		// An item written to another municipality is not among those of the municipality asked for
		item.setMunicipalityId("2262");
		assertThat(service.getItems(MUNICIPALITY_ID, null, null, null)).isEmpty();
		assertThatThrownBy(() -> service.getItem(MUNICIPALITY_ID, new ItemKey(Source.ALKOL, ANMALAN, "1-FOLKOL")))
			.isInstanceOfSatisfying(ThrowableProblem.class, problem -> assertThat(problem.getStatus()).isEqualTo(NOT_FOUND));
		assertThatThrownBy(() -> service.getItem(MUNICIPALITY_ID, new ItemKey(Source.ALKOL, ANMALAN, "2-FOLKOL")))
			.isInstanceOfSatisfying(ThrowableProblem.class, problem -> assertThat(problem.getStatus()).isEqualTo(NOT_FOUND));
	}

	private static MigrationRunEntity runEntity(final RunStatus status) {
		final var run = new MigrationRunEntity();
		run.setId("run-id");
		run.setMunicipalityId(MUNICIPALITY_ID);
		run.setSources("ALKT");
		run.setKinds("TILLSTAND");
		run.setStatus(status);
		return run;
	}

	private static RunItemEntity line(final Outcome outcome) {
		final var line = new RunItemEntity();
		line.setOutcome(outcome);
		return line;
	}

	private void finishAs(final RunStatus status, final String message) {
		doAnswer(invocation -> {
			final MigrationRunEntity run = invocation.getArgument(0);
			run.setStatus(status);
			run.setMessage(message);
			return Optional.empty();
		}).when(runner).execute(any(), any(), any());
	}
}
