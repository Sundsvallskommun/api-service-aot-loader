package se.sundsvall.aotloader.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.integration.db.MigrationRunRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationRunEntity;
import se.sundsvall.aotloader.service.model.FilePurpose;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.PartyType;
import se.sundsvall.aotloader.service.model.RunStatus;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.source.files.FileArea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;

class RunSupportTest {

	@Test
	void runLock() {
		final var lock = new RunLock();

		assertThat(lock.holder()).isEmpty();
		assertThat(lock.tryAcquire("a")).isTrue();
		assertThat(lock.tryAcquire("b")).isFalse();
		lock.release("b");
		assertThat(lock.holder()).contains("a");
		lock.release("a");
		assertThat(lock.tryAcquire("b")).isTrue();
	}

	@Test
	void runContext() {
		final var stop = new AtomicBoolean();
		final var fileAreas = new AtomicInteger();
		final var check = new Preflight.Result(List.of(), Map.of("2281", Map.of("A", "1")), Map.of("2281", Map.of("SERVING_AREA_DRAWING", new FilePurpose("p-1", "SERVING_AREA_DRAWING", "Ritning"))));
		final var context = new RunContext("run", "2281", true, check, 2, stop, _ -> {
			fileAreas.incrementAndGet();
			return new FileArea(null, 1);
		});
		final var lookups = new AtomicInteger();

		assertThat(context.runId()).isEqualTo("run");
		assertThat(context.municipalityId()).isEqualTo("2281");
		assertThat(context.dryRun()).isTrue();
		assertThat(context.labelIds("2281")).containsEntry("A", "1");
		assertThat(context.labelIds("2262")).isEmpty();
		assertThat(context.purpose("2281", "SERVING_AREA_DRAWING")).contains(new FilePurpose("p-1", "SERVING_AREA_DRAWING", "Ritning"));
		assertThat(context.purpose("2281", "BUDGET")).isEmpty();
		assertThat(context.purpose("2262", "SERVING_AREA_DRAWING")).isEmpty();
		assertThat(context.partyId("2281", TestData.HOLDER_ID, _ -> Optional.of("p" + lookups.incrementAndGet()))).contains("p1");
		assertThat(context.partyId("2281", TestData.HOLDER_ID, _ -> Optional.of("p" + lookups.incrementAndGet()))).contains("p1");
		// Party is asked again for another municipality
		assertThat(context.partyId("2262", TestData.HOLDER_ID, _ -> Optional.of("p" + lookups.incrementAndGet()))).contains("p2");
		assertThat(context.partyId("2281", TestData.PERSON_ID, _ -> Optional.empty())).isEmpty();
		final var failing = new LegalId("190001011234", PartyType.PRIVATE);
		assertThatThrownBy(() -> context.partyId("2281", failing, _ -> {
			throw new IllegalStateException();
		})).isInstanceOf(IllegalStateException.class);
		// A failed lookup is not remembered
		assertThat(context.partyId("2281", failing, _ -> Optional.of("later"))).contains("later");
		assertThat(context.fileArea(Source.ALKT)).isSameAs(context.fileArea(Source.ALKT));
		assertThat(fileAreas).hasValue(1);
		assertThat(context.outcomeOf(new ItemKey(Source.ALKT, TILLSTAND, "1"))).isEmpty();
		context.recordOutcome(new ItemKey(Source.ALKT, TILLSTAND, "1"), Outcome.GRANSKAS);
		assertThat(context.outcomeOf(new ItemKey(Source.ALKT, TILLSTAND, "1"))).contains(Outcome.GRANSKAS);
		assertThat(context.tryCountWrite()).isTrue();
		assertThat(context.tryCountWrite()).isTrue();
		assertThat(context.tryCountWrite()).isFalse();
		assertThat(context.stopRequested()).isFalse();
		stop.set(true);
		assertThat(context.stopRequested()).isTrue();
	}

	@Test
	void runRecoveryMarksInterruptedRuns() {
		final var repository = mock(MigrationRunRepository.class);
		final var run = new MigrationRunEntity();
		run.setId("run");
		run.setStatus(RunStatus.RUNNING);
		when(repository.findByStatus(RunStatus.RUNNING)).thenReturn(List.of(run));

		new RunRecovery(repository, Clock.fixed(Instant.parse("2026-10-12T08:00:00Z"), ZoneId.of("Europe/Stockholm"))).abortInterruptedRuns();

		assertThat(run.getStatus()).isEqualTo(RunStatus.ABORTED);
		assertThat(run.getFinished()).isNotNull();
		assertThat(run.getMessage()).startsWith("Avbruten");
		verify(repository).save(run);
	}
}
