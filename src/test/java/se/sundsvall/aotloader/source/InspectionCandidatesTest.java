package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import se.sundsvall.aotloader.service.model.InvestigationData;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.source.InspectionCandidates.InspectionErrand;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.aotloader.service.model.Source.ALKT;

class InspectionCandidatesTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "-", textBlock = """
		2021-01-01 | 2021-03-01 | 2020-12-28 | 2021-02-15T10:30 | 2021-01-01T00:00 | 2021-03-01T00:00
		-          | 2021-03-01 | 2020-12-28 | -                | 2020-12-28T00:00 | 2021-03-01T00:00
		-          | 1999-05-01 | 2004-03-15 | -                | 1999-05-01T00:00 | 1999-05-01T00:00
		2021-01-01 | -          | -          | 2021-02-15T10:30 | 2021-01-01T00:00 | 2021-02-15T10:30
		2021-01-01 | -          | -          | -                | 2021-01-01T00:00 | 2021-01-01T00:00
		-          | 2021-03-01 | -          | -                | 2021-03-01T00:00 | 2021-03-01T00:00
		-          | -          | -          | 2021-02-15T10:30 | 2021-02-15T10:30 | 2021-02-15T10:30
		2021-03-01 | 2021-01-01 | -          | -                | 2021-03-01T00:00 | 2021-03-01T00:00
		2021-01-01 | -          | -          | 2020-12-01T09:00 | 2021-01-01T00:00 | 2021-01-01T00:00
		-          | -          | -          | -                | -                | -
		""")
	void anInspectionErrandStartsWhenOpenedAndEndsWhenClosedOrAtItsLatestEventOrDecision(final LocalDate opened, final LocalDate closed, final LocalDate registered,
		final LocalDateTime lastActivity, final LocalDateTime startedAt, final LocalDateTime completedAt) {
		final var errand = new InspectionErrand(ALKT, 700, "07", "Tillsyn", null, opened, closed, List.of(), registered, lastActivity);

		assertThat(InspectionCandidates.investigation("Tillsyn", errand, InvestigationTexts.NONE)).isEqualTo(new InvestigationData("Tillsyn", startedAt, completedAt));
	}
}
