package se.sundsvall.aotloader.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import se.sundsvall.aotloader.support.MigrationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYN;

@ExtendWith(MockitoExtension.class)
class MigrationRunnerTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T08:00:00Z"), ZoneId.of("Europe/Stockholm"));

	@Mock
	private SourceReader alktReader;

	@Mock
	private SourceReader alkolReader;

	@Mock
	private ItemMigrator migrator;

	@Mock
	private Preflight preflight;

	@Mock
	private TargetGuard targetGuard;

	@Mock
	private MigrationRunRepository runRepository;

	@Mock
	private RunItemRepository runItemRepository;

	private final List<RunItemEntity> lines = new ArrayList<>();
	private MigrationRunner runner;

	@BeforeEach
	void setUp() {
		when(alktReader.source()).thenReturn(Source.ALKT);
		when(alkolReader.source()).thenReturn(Source.ALKOL);
		lenient().when(alktReader.kinds()).thenReturn(Set.of(TILLSTAND, TILLSYN));
		lenient().when(alkolReader.kinds()).thenReturn(Set.of(Kind.values()));
		lenient().when(targetGuard.check()).thenReturn(Optional.empty());
		lenient().when(preflight.check(any(), anyMap())).thenReturn(new Preflight.Result(List.of(), Map.of("2281", TestData.labelIds()), Map.of("2281", TestData.purposes())));
		lenient().when(runItemRepository.save(any())).thenAnswer(invocation -> {
			lines.add(invocation.getArgument(0));
			return invocation.getArgument(0);
		});
		runner = new MigrationRunner(List.of(alktReader, alkolReader), migrator, preflight, targetGuard, runRepository, runItemRepository, TestData.properties(), CLOCK);
	}

	@Test
	void supportedKinds() {
		assertThat(runner.supportedKinds()).containsEntry(Source.ALKT, Set.of(TILLSTAND, TILLSYN)).containsEntry(Source.ALKOL, Set.of(Kind.values()));
	}

	@Test
	void aFullRunTakesEveryItemAndListsTheNotices() {
		final var permit = TestData.permit("1");
		final var other = TestData.permit("2");
		final var notice = new Notice(new ItemKey(Source.ALKT, TILLSTAND, "utan-id-1"), null, Outcome.GRANSKAS, "G1");
		when(alktReader.read(Set.of(TILLSTAND))).thenReturn(new ReaderResult(List.of(permit, other), List.of(notice)));
		when(migrator.process(eq(permit), any())).thenReturn(new ItemResult(Outcome.MIGRERAD, List.of("G13"), "G13 ...", "ALKT-1"));
		when(migrator.process(eq(other), any())).thenReturn(new ItemResult(Outcome.FEL, List.of(), "Steg Ärende: 502 med 198001011234", null));
		final var run = run(false, null);

		final var result = runner.execute(run, Map.of(Source.ALKT, Set.of(TILLSTAND)), Optional.empty());

		assertThat(result).isEmpty();
		assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
		assertThat(run.getFinished()).isNotNull();
		assertThat(run.getMessage()).isNull();
		assertThat(lines).extracting(RunItemEntity::getSourceId, RunItemEntity::getOutcome).containsExactly(
			tuple("utan-id-1", Outcome.GRANSKAS),
			tuple("1", Outcome.MIGRERAD),
			tuple("2", Outcome.FEL));
		assertThat(lines.get(1).getRules()).isEqualTo("G13");
		assertThat(lines.get(1).getPlaceName()).isEqualTo("Restaurang 1");
		assertThat(lines.get(1).getPlaceNumber()).isEqualTo("12345678");
		assertThat(lines.get(1).getErrandNumber()).isEqualTo("ALKT-1");
		assertThat(lines.get(2).getDetail()).isEqualTo("Steg Ärende: 502 med [nummer]");
		verify(runRepository).save(run);
		verify(alkolReader, never()).read(any());
	}

	@Test
	void aSourceThatCannotBeReadDoesNotStopTheOthers() {
		when(alktReader.read(Set.of(TILLSTAND))).thenThrow(new MigrationException("Källdatabasen för AlkT är inte konfigurerad"));
		when(alkolReader.read(Set.of(TILLSTAND))).thenReturn(new ReaderResult(List.of(TestData.permit("9")), List.of()));
		when(migrator.process(any(), any())).thenReturn(new ItemResult(Outcome.SKULLE_MIGRERAS, List.of(), null, null));
		final var run = run(true, null);

		runner.execute(run, Map.of(Source.ALKT, Set.of(TILLSTAND), Source.ALKOL, Set.of(TILLSTAND)), Optional.empty());

		assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
		assertThat(run.getMessage()).isEqualTo("AlkT kunde inte läsas: Källdatabasen för AlkT är inte konfigurerad");
		assertThat(lines).hasSize(1);
	}

	@Test
	void aRealRunDoesNotStartWhenTheChecksFail() {
		when(preflight.check(any(), anyMap())).thenReturn(new Preflight.Result(List.of("Rollen saknas", "Etiketten saknas"), Map.of(), Map.of()));
		final var run = run(false, null);

		runner.execute(run, Map.of(Source.ALKT, Set.of(TILLSTAND)), Optional.empty());

		assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
		assertThat(run.getMessage()).isEqualTo("Körningen startade inte. Kontrollen före körning: Rollen saknas; Etiketten saknas");
		verify(alktReader, never()).read(any());
	}

	@Test
	void aDryRunGoesOnAndListsWhatTheChecksFound() {
		when(preflight.check(any(), anyMap())).thenReturn(new Preflight.Result(List.of("Rollen saknas"), Map.of(), Map.of()));
		when(alktReader.read(Set.of(TILLSTAND))).thenReturn(new ReaderResult(List.of(), List.of()));
		final var run = run(true, null);

		runner.execute(run, Map.of(Source.ALKT, Set.of(TILLSTAND)), Optional.empty());

		assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
		assertThat(run.getMessage()).isEqualTo("Kontrollen före körning, som stoppar en skarp körning: Rollen saknas");
	}

	@Test
	void aDatabaseOfAnotherEnvironmentStopsEveryRun() {
		when(targetGuard.check()).thenReturn(Optional.of("Den lokala databasen hör till en annan miljö"));
		final var run = run(true, null);

		runner.execute(run, Map.of(Source.ALKT, Set.of(TILLSTAND)), Optional.empty());

		assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
		assertThat(run.getMessage()).isEqualTo("Körningen startade inte. Den lokala databasen hör till en annan miljö");
		verify(preflight, never()).check(any(), anyMap());
	}

	@Test
	void theLimitAndItemsNotRun() {
		when(alktReader.read(Set.of(TILLSTAND))).thenReturn(new ReaderResult(List.of(TestData.permit("1"), TestData.permit("2")), List.of()));
		when(migrator.process(any(), any())).thenReturn(new ItemResult(Outcome.MIGRERAD, List.of(), null, "ALKT-1"), new ItemResult(Outcome.EJ_KORD, List.of(), null, null));
		final var run = run(false, 1);

		runner.execute(run, Map.of(Source.ALKT, Set.of(TILLSTAND)), Optional.empty());

		assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
		assertThat(run.getMessage()).isEqualTo("Gränsen på 1 ärenden nåddes; resten tas i nästa körning");
		assertThat(lines).hasSize(1);
	}

	@Test
	void aRunStopsWhenAskedTo() {
		final var run = run(false, null);
		when(alktReader.read(Set.of(TILLSTAND))).thenReturn(new ReaderResult(List.of(TestData.permit("1"), TestData.permit("2")), List.of()));
		when(migrator.process(any(), any())).thenAnswer(_ -> {
			assertThat(runner.requestStop(run.getId())).isTrue();
			return new ItemResult(Outcome.MIGRERAD, List.of(), null, null);
		});

		runner.execute(run, Map.of(Source.ALKT, Set.of(TILLSTAND)), Optional.empty());

		assertThat(run.getStatus()).isEqualTo(RunStatus.STOPPED);
		assertThat(run.getMessage()).isEqualTo("Körningen stoppades på begäran");
		verify(migrator, times(1)).process(any(), any());
		assertThat(runner.requestStop(run.getId())).isFalse();
	}

	@Test
	void aSingleItem() {
		final Candidate permit = TestData.permit("2");
		when(alktReader.read(Set.of(TILLSTAND))).thenReturn(new ReaderResult(List.of(TestData.permit("1"), permit), List.of(new Notice(new ItemKey(Source.ALKT, TILLSTAND, "x"), null, Outcome.GRANSKAS, null))));
		final var itemResult = new ItemResult(Outcome.SKULLE_MIGRERAS, List.of(), null, null);
		when(migrator.process(eq(permit), any())).thenReturn(itemResult);

		final var result = runner.execute(run(true, null), Map.of(Source.ALKT, Set.of(TILLSTAND)), Optional.of(permit.key()));

		assertThat(result).contains(itemResult);
		assertThat(lines).singleElement().extracting(RunItemEntity::getSourceId).isEqualTo("2");
	}

	@Test
	void aSingleItemThatIsNotFound() {
		when(alktReader.read(Set.of(TILLSTAND))).thenReturn(new ReaderResult(List.of(TestData.permit("1")), List.of()));
		final var run = run(true, null);

		final var result = runner.execute(run, Map.of(Source.ALKT, Set.of(TILLSTAND)), Optional.of(new ItemKey(Source.ALKT, TILLSTAND, "9")));

		assertThat(result).isEmpty();
		assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
		assertThat(run.getMessage()).isEqualTo("Ärendet ALKT/TILLSTAND/9 finns inte i källan, eller är inte ett ärende som migreras");
	}

	@Test
	void anUnexpectedErrorEndsTheRun() {
		when(alktReader.read(Set.of(TILLSTAND))).thenReturn(new ReaderResult(List.of(TestData.permit("1")), List.of()));
		when(migrator.process(any(), any())).thenThrow(new IllegalStateException("Oväntat"));
		when(runRepository.save(any())).thenThrow(new IllegalStateException("Databasen"));
		final var run = run(false, null);

		runner.execute(run, Map.of(Source.ALKT, Set.of(TILLSTAND)), Optional.empty());

		assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
		assertThat(run.getMessage()).isEqualTo("Körningen avbröts av ett oväntat fel: IllegalStateException: Oväntat");
	}

	@Test
	void anErrorIsSavedAsAFailedRunAndPassedOn() {
		when(alktReader.read(Set.of(TILLSTAND))).thenReturn(new ReaderResult(List.of(TestData.permit("1")), List.of()));
		when(migrator.process(any(), any())).thenThrow(new StackOverflowError());
		final var run = run(false, null);
		final var scope = Map.of(Source.ALKT, Set.of(TILLSTAND));
		final Optional<ItemKey> single = Optional.empty();

		assertThatThrownBy(() -> runner.execute(run, scope, single)).isInstanceOf(StackOverflowError.class);

		assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
		assertThat(run.getMessage()).isEqualTo("Körningen avbröts av ett oväntat fel: StackOverflowError");
		verify(runRepository).save(run);
		assertThat(runner.requestStop(run.getId())).isFalse();
	}

	@Test
	void aRunThatFailedStaysFailedWhenItIsStopped() {
		final var run = run(true, null);
		when(alktReader.read(Set.of(TILLSTAND))).thenThrow(new MigrationException("Källdatabasen för AlkT är inte konfigurerad"));
		when(alkolReader.read(Set.of(TILLSTAND))).thenReturn(new ReaderResult(List.of(TestData.permit("9")), List.of()));
		when(preflight.check(any(), anyMap())).thenAnswer(_ -> {
			runner.requestStop(run.getId());
			return new Preflight.Result(List.of(), Map.of(), Map.of());
		});

		runner.execute(run, Map.of(Source.ALKT, Set.of(TILLSTAND), Source.ALKOL, Set.of(TILLSTAND)), Optional.empty());

		assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
		assertThat(run.getMessage()).isEqualTo("AlkT kunde inte läsas: Källdatabasen för AlkT är inte konfigurerad\nKörningen stoppades på begäran");
		verify(migrator, never()).process(any(), any());
	}

	@Test
	void aLongDetailIsCutToTheLengthOfItsColumn() {
		when(alktReader.read(Set.of(TILLSTAND))).thenReturn(new ReaderResult(List.of(TestData.permit("1")), List.of()));
		when(migrator.process(any(), any())).thenReturn(new ItemResult(Outcome.FEL, List.of(), "x".repeat(5000), null));

		runner.execute(run(false, null), Map.of(Source.ALKT, Set.of(TILLSTAND)), Optional.empty());

		assertThat(lines).singleElement().extracting(RunItemEntity::getDetail).asString().hasSize(4000);
	}

	@Test
	void aReportRowThatCannotBeSavedDoesNotStopTheRun() {
		when(alktReader.read(Set.of(TILLSTAND))).thenReturn(new ReaderResult(List.of(TestData.permit("1"), TestData.permit("2")), List.of()));
		when(migrator.process(any(), any())).thenReturn(new ItemResult(Outcome.MIGRERAD, List.of(), null, null));
		when(runItemRepository.save(any())).thenThrow(new IllegalStateException("Databasen"));
		final var run = run(false, null);

		runner.execute(run, Map.of(Source.ALKT, Set.of(TILLSTAND)), Optional.empty());

		assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
		verify(migrator, times(2)).process(any(), any());
	}

	private static MigrationRunEntity run(final boolean dryRun, final Integer limit) {
		final var run = new MigrationRunEntity();
		run.setId("run-" + dryRun + "-" + limit);
		run.setMunicipalityId("2281");
		run.setDryRun(dryRun);
		run.setItemLimit(limit);
		run.setStatus(RunStatus.RUNNING);
		run.setStarted(OffsetDateTime.now(CLOCK));
		return run;
	}
}
