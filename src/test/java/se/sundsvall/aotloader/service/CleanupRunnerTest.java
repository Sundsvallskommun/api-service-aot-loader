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
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.aotloader.integration.db.MigrationItemRepository;
import se.sundsvall.aotloader.integration.db.MigrationRunRepository;
import se.sundsvall.aotloader.integration.db.RunItemRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationItemEntity;
import se.sundsvall.aotloader.integration.db.model.MigrationRunEntity;
import se.sundsvall.aotloader.integration.db.model.RunItemEntity;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.RunStatus;
import se.sundsvall.aotloader.service.model.Source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static se.sundsvall.aotloader.service.model.Kind.ANMALAN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYNSBESOK;

@ExtendWith(MockitoExtension.class)
class CleanupRunnerTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T08:00:00Z"), ZoneId.of("Europe/Stockholm"));
	private static final Map<Source, Set<Kind>> EVERYTHING = Map.of(Source.ALKT, Set.of(TILLSTAND, TILLSYN, TILLSYNSBESOK), Source.ALKOL, Set.of(Kind.values()));
	private static final AtomicLong IDS = new AtomicLong();

	@Mock
	private ItemCleaner cleaner;

	@Mock
	private TargetGuard targetGuard;

	@Mock
	private MigrationItemRepository itemRepository;

	@Mock
	private MigrationRunRepository runRepository;

	@Mock
	private RunItemRepository runItemRepository;

	private final List<RunItemEntity> lines = new ArrayList<>();
	private CleanupRunner runner;

	@BeforeEach
	void setUp() {
		lenient().when(targetGuard.check()).thenReturn(Optional.empty());
		lenient().when(runItemRepository.save(any())).thenAnswer(invocation -> {
			lines.add(invocation.getArgument(0));
			return invocation.getArgument(0);
		});
		lenient().when(cleaner.clean(any(), anyString(), anyBoolean())).thenAnswer(invocation -> {
			final MigrationItemEntity item = invocation.getArgument(0);
			return new ItemResult(Outcome.RENSAD, List.of(), "Borttaget: ärendet i SupportManagement och raden i statustabellen", item.getErrandNumber());
		});
		runner = new CleanupRunner(cleaner, targetGuard, itemRepository, new RunRecords(runRepository, runItemRepository, new ResultFiles(runItemRepository, null, CLOCK), CLOCK));
	}

	@Test
	void theVisitsGoFirstAndThePermitsLastAndAnItemOfAnotherMunicipalityStays() {
		final var permit = item(Source.ALKT, TILLSTAND, "1", "2281");
		final var notification = item(Source.ALKOL, ANMALAN, "5-FOLKOL", "2281");
		final var inspection = item(Source.ALKT, TILLSYN, "700", "2281");
		final var visit = item(Source.ALKOL, TILLSYNSBESOK, "950", "2281");
		final var reviewed = item(Source.ALKT, TILLSTAND, "2", null);
		final var elsewhere = item(Source.ALKT, TILLSTAND, "3", "2262");
		when(itemRepository.findAllByOrderBySourceAscKindAscSourceIdAsc()).thenReturn(List.of(permit, reviewed, elsewhere, inspection, notification, visit));
		final var run = run(false, null);

		assertThat(runner.execute(run, EVERYTHING, Optional.empty())).isEmpty();

		assertThat(lines).extracting(RunItemEntity::getSourceId, RunItemEntity::getOutcome, RunItemEntity::getRules, RunItemEntity::getPlaceName)
			.containsExactly(
				tuple("950", Outcome.RENSAD, "", "Ställe 950"),
				tuple("700", Outcome.RENSAD, "", "Ställe 700"),
				tuple("5-FOLKOL", Outcome.RENSAD, "", "Ställe 5-FOLKOL"),
				tuple("1", Outcome.RENSAD, "", "Ställe 1"),
				tuple("2", Outcome.RENSAD, "", "Ställe 2"));
		verify(cleaner, never()).clean(elsewhere, "2281", false);
		assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
		assertThat(run.getFinished()).isEqualTo(OffsetDateTime.now(CLOCK));
		verify(runRepository).save(run);
	}

	@Test
	void aSelectionCleansUpOnlyItsItemsAndTellsThoseNotInTheStatusTable() {
		final var permit = item(Source.ALKT, TILLSTAND, "1", "2281");
		final var inspection = item(Source.ALKT, TILLSYN, "700", "2281");
		final var other = item(Source.ALKT, TILLSYN, "701", "2281");
		when(itemRepository.findAllByOrderBySourceAscKindAscSourceIdAsc()).thenReturn(List.of(permit, inspection, other));
		final var run = run(false, null);
		run.setItemKeys("ALKT/TILLSTAND/1,ALKT/TILLSYN/700,ALKT/TILLSYN/999");

		assertThat(runner.execute(run, Map.of(Source.ALKT, Set.of(TILLSTAND, TILLSYN)), Optional.empty())).isEmpty();

		assertThat(lines).extracting(RunItemEntity::getSourceId).containsExactly("700", "1");
		verify(cleaner, never()).clean(other, "2281", false);
		assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
		assertThat(run.getMessage()).isEqualTo("1 av ärendena i urvalet finns inte i statustabellen: ALKT/TILLSYN/999");
	}

	@Test
	void onlyTheScopeIsCleanedUpAndADryRunIsPassedOn() {
		final var permit = item(Source.ALKT, TILLSTAND, "1", "2281");
		final var inspection = item(Source.ALKT, TILLSYN, "700", "2281");
		when(itemRepository.findAllByOrderBySourceAscKindAscSourceIdAsc()).thenReturn(List.of(permit, inspection));

		runner.execute(run(true, null), Map.of(Source.ALKT, Set.of(TILLSYN)), Optional.empty());

		verify(cleaner).clean(inspection, "2281", true);
		verify(cleaner, never()).clean(permit, "2281", true);
	}

	@Test
	void theLimitStopsTheCleanupWithANote() {
		when(itemRepository.findAllByOrderBySourceAscKindAscSourceIdAsc()).thenReturn(List.of(item(Source.ALKT, TILLSTAND, "1", "2281"), item(Source.ALKT, TILLSTAND, "2", "2281")));
		final var run = run(false, 1);

		runner.execute(run, EVERYTHING, Optional.empty());

		assertThat(lines).extracting(RunItemEntity::getSourceId).containsExactly("1");
		assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
		assertThat(run.getMessage()).isEqualTo("Gränsen på 1 ärenden nåddes; resten tas i nästa rensning");
	}

	@Test
	void aSingleItemThatIsNotInTheStatusTableIsTold() {
		when(itemRepository.findAllByOrderBySourceAscKindAscSourceIdAsc()).thenReturn(List.of(item(Source.ALKT, TILLSTAND, "1", "2281")));
		final var run = run(false, null);
		final var key = new ItemKey(Source.ALKT, TILLSTAND, "9");

		assertThat(runner.execute(run, Map.of(Source.ALKT, Set.of(TILLSTAND)), Optional.of(key))).isEmpty();

		assertThat(lines).isEmpty();
		assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
		assertThat(run.getMessage()).isEqualTo("Ärendet ALKT/TILLSTAND/9 finns inte i statustabellen");
	}

	@Test
	void aSingleItemGivesItsResult() {
		final var permit = item(Source.ALKT, TILLSTAND, "1", "2281");
		permit.setErrandNumber("ALKT-2026-1");
		when(itemRepository.findAllByOrderBySourceAscKindAscSourceIdAsc()).thenReturn(List.of(item(Source.ALKT, TILLSTAND, "2", "2281"), permit));

		final var result = runner.execute(run(false, null), Map.of(Source.ALKT, Set.of(TILLSTAND)), Optional.of(new ItemKey(Source.ALKT, TILLSTAND, "1")));

		assertThat(result).map(RunItemEntity::getErrandNumber).contains("ALKT-2026-1");
		assertThat(lines).extracting(RunItemEntity::getSourceId, RunItemEntity::getErrandNumber).containsExactly(tuple("1", "ALKT-2026-1"));
	}

	@Test
	void aDatabaseOfAnotherEnvironmentIsNotCleanedUp() {
		when(targetGuard.check()).thenReturn(Optional.of("Den lokala databasen hör till en annan miljö"));
		final var run = run(false, null);

		runner.execute(run, EVERYTHING, Optional.empty());

		verify(itemRepository, never()).findAllByOrderBySourceAscKindAscSourceIdAsc();
		assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
		assertThat(run.getMessage()).isEqualTo("Rensningen startade inte. Den lokala databasen hör till en annan miljö");
	}

	@Test
	void aStopTakesEffectBeforeTheNextItem() {
		final var run = run(false, null);
		when(itemRepository.findAllByOrderBySourceAscKindAscSourceIdAsc()).thenReturn(List.of(item(Source.ALKT, TILLSTAND, "1", "2281"), item(Source.ALKT, TILLSTAND, "2", "2281")));
		doAnswer(_ -> {
			assertThat(runner.requestStop(run.getId())).isTrue();
			return new ItemResult(Outcome.RENSAD, List.of(), null, null);
		}).when(cleaner).clean(any(), anyString(), anyBoolean());

		runner.execute(run, EVERYTHING, Optional.empty());

		assertThat(lines).extracting(RunItemEntity::getSourceId).containsExactly("1");
		assertThat(run.getStatus()).isEqualTo(RunStatus.STOPPED);
		assertThat(runner.requestStop(run.getId())).isFalse();
	}

	@Test
	void anUnexpectedErrorFailsTheCleanup() {
		when(itemRepository.findAllByOrderBySourceAscKindAscSourceIdAsc()).thenThrow(new IllegalStateException("Databasen svarar inte"));
		final var run = run(false, null);

		runner.execute(run, EVERYTHING, Optional.empty());

		assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
		assertThat(run.getMessage()).startsWith("Rensningen avbröts av ett oväntat fel:");
	}

	private static MigrationRunEntity run(final boolean dryRun, final Integer limit) {
		final var run = new MigrationRunEntity();
		run.setId("cleanup-" + dryRun + "-" + limit);
		run.setMunicipalityId("2281");
		run.setDryRun(dryRun);
		run.setCleanup(true);
		run.setItemLimit(limit);
		run.setStatus(RunStatus.RUNNING);
		return run;
	}

	private static MigrationItemEntity item(final Source source, final Kind kind, final String sourceId, final String municipalityId) {
		final var item = new MigrationItemEntity();
		// An entity equals another by its id
		item.setId(IDS.incrementAndGet());
		item.setSource(source);
		item.setKind(kind);
		item.setSourceId(sourceId);
		item.setMunicipalityId(municipalityId);
		item.setPlaceName("Ställe " + sourceId);
		return item;
	}
}
