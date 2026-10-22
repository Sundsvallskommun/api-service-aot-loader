package se.sundsvall.aotloader.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.aotloader.api.model.ReviewExceptionRequest;
import se.sundsvall.aotloader.integration.db.ReviewExceptionRepository;
import se.sundsvall.aotloader.integration.db.model.ReviewExceptionEntity;
import se.sundsvall.aotloader.service.model.ExceptionOutcome;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYN;

@ExtendWith(MockitoExtension.class)
class ReviewExceptionServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T08:00:00Z"), ZoneId.of("Europe/Stockholm"));

	@Mock
	private MigrationRunner runner;

	@Mock
	private ReviewExceptionRepository exceptionRepository;

	private ReviewExceptionService service;

	@BeforeEach
	void setUp() {
		lenient().when(runner.supportedKinds()).thenReturn(Map.of(Source.ALKT, Set.of(TILLSTAND, TILLSYN), Source.ALKOL, Set.of(Kind.values())));
		service = new ReviewExceptionService(runner, exceptionRepository, CLOCK);
	}

	@ParameterizedTest
	@CsvSource({
		"ALKT, TILLSTAND, 0091",
		"ALKT, TILLSTAND, 91-FOLKOL",
		"ALKT, ANMALAN, 91-FOLKOL",
		"ALKOL, ANMALAN, 91",
		"ALKOL, ANMALAN, 91-SNUS",
		"ALKOL, ANMALAN, 091-FOLKOL"
	})
	void anExceptionNoItemCanHaveIsRefused(final Source source, final Kind kind, final String sourceId) {
		final var request = new ReviewExceptionRequest(ExceptionOutcome.MIGRERA, "handlaggare", null, null);

		assertThatThrownBy(() -> service.putException(new ItemKey(source, kind, sourceId), request))
			.isInstanceOfSatisfying(ThrowableProblem.class, problem -> assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST));
		verify(exceptionRepository, never()).save(any());
	}

	@Test
	void aNotificationOfAKnownProductIsTaken() {
		when(exceptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		assertThat(service.putException(new ItemKey(Source.ALKOL, Kind.ANMALAN, "91-FOLKOL"), new ReviewExceptionRequest(ExceptionOutcome.MIGRERA, "handlaggare", null, null)).sourceId())
			.isEqualTo("91-FOLKOL");
	}

	@Test
	void exceptions() {
		final var key = new ItemKey(Source.ALKT, TILLSTAND, "1");
		when(exceptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		final var created = service.putException(key, new ReviewExceptionRequest(ExceptionOutcome.MIGRERA, " handlaggare ", null, "Kommentar"));

		assertThat(created.outcome()).isEqualTo(ExceptionOutcome.MIGRERA);
		assertThat(created.decidedBy()).isEqualTo("handlaggare");
		assertThat(created.decidedAt()).isEqualTo(LocalDate.of(2026, 10, 12));
		assertThat(created.sourceId()).isEqualTo("1");

		final var existing = new ReviewExceptionEntity();
		existing.setSource(Source.ALKT);
		existing.setKind(TILLSTAND);
		existing.setSourceId("1");
		lenient().when(exceptionRepository.findBySourceAndKindAndSourceId(Source.ALKT, TILLSTAND, "1")).thenReturn(Optional.of(existing));
		when(exceptionRepository.findAllByOrderBySourceAscKindAscSourceIdAsc()).thenReturn(List.of(existing));

		final var changed = service.putException(key, new ReviewExceptionRequest(ExceptionOutcome.MIGRERA_SOM_UTGANGET, "annan", LocalDate.of(2026, 9, 1), null));
		assertThat(changed.outcome()).isEqualTo(ExceptionOutcome.MIGRERA_SOM_UTGANGET);
		assertThat(changed.decidedAt()).isEqualTo(LocalDate.of(2026, 9, 1));
		assertThat(service.getExceptions()).hasSize(1);

		service.deleteException(key);
		verify(exceptionRepository).delete(existing);

		assertThatThrownBy(() -> service.deleteException(new ItemKey(Source.ALKT, TILLSTAND, "2")))
			.isInstanceOfSatisfying(ThrowableProblem.class, problem -> assertThat(problem.getStatus()).isEqualTo(NOT_FOUND));
	}
}
