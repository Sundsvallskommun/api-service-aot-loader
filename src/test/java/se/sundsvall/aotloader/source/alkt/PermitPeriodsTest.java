package se.sundsvall.aotloader.source.alkt;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static se.sundsvall.aotloader.source.alkt.AlktTestData.permit;

class PermitPeriodsTest {

	private static final MonthDay MAY_1 = MonthDay.of(5, 1);
	private static final MonthDay SEPTEMBER_30 = MonthDay.of(9, 30);

	static Stream<Arguments> periods() {
		return Stream.of(
			argumentSet("neither season nor period", null, null, null, null,
				new PermitPeriods(null, null, null, null, null, null, List.of())),
			argumentSet("season", "1 maj", "30 sept", null, null,
				new PermitPeriods(MAY_1, SEPTEMBER_30, null, null, null, null, List.of())),
			argumentSet("season that cannot be interpreted", "vår", "höst", null, null,
				new PermitPeriods(null, null, null, null, "vår–höst", null, List.of("säsong 'vår–höst'"))),
			argumentSet("season without end", "1 maj", null, null, null,
				new PermitPeriods(null, null, null, null, "1 maj–", null, List.of("säsong '1 maj–'"))),
			argumentSet("date period", null, null, "2026-05-01", "2026-09-30 00:00:00",
				new PermitPeriods(null, null, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 9, 30), null, null, List.of())),
			argumentSet("date period without end", null, null, "2026-05-01", null,
				new PermitPeriods(null, null, LocalDate.of(2026, 5, 1), null, null, null, List.of("perioden '2026-05-01–' saknar slut"))),
			argumentSet("date period without start", null, null, null, "2026-09-30",
				new PermitPeriods(null, null, null, LocalDate.of(2026, 9, 30), null, null, List.of("perioden '–2026-09-30' saknar början"))),
			argumentSet("date period that ends before it starts", null, null, "2026-09-30", "2026-05-01",
				new PermitPeriods(null, null, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 5, 1), null, null, List.of("perioden '2026-09-30–2026-05-01' slutar före sin början"))),
			argumentSet("period of days and months is the season", null, null, "05-01", "09-30",
				new PermitPeriods(MAY_1, SEPTEMBER_30, null, null, null, null, List.of())),
			argumentSet("period of days and months equal to the season", "1 maj", "30 sep", "05-01", "09-30",
				new PermitPeriods(MAY_1, SEPTEMBER_30, null, null, null, null, List.of())),
			argumentSet("period of days and months other than the season", "1 maj", "30 sep", "06-01", "08-31",
				new PermitPeriods(MAY_1, SEPTEMBER_30, null, null, null, "06-01–08-31", List.of("period '06-01–08-31'"))),
			argumentSet("period of days and months with a season that cannot be interpreted", "vår", "höst", "05-01", "09-30",
				new PermitPeriods(MAY_1, SEPTEMBER_30, null, null, "vår–höst", null, List.of("säsong 'vår–höst'"))),
			argumentSet("period of a date and a day", null, null, "05-01", "2026-09-30",
				new PermitPeriods(null, null, null, null, null, "05-01–2026-09-30", List.of("period '05-01–2026-09-30'"))),
			argumentSet("period that cannot be interpreted", null, null, "maj", "2026-09-30",
				new PermitPeriods(null, null, null, null, null, "maj–2026-09-30", List.of("period 'maj–2026-09-30'"))),
			argumentSet("date period with a time that cannot be interpreted", null, null, "2026-05-01 xx", "2026-09-30",
				new PermitPeriods(null, null, null, null, null, "2026-05-01 xx–2026-09-30", List.of("period '2026-05-01 xx–2026-09-30'"))));
	}

	@ParameterizedTest
	@MethodSource("periods")
	void of(final String seasonStart, final String seasonEnd, final String periodStart, final String periodEnd, final PermitPeriods expected) {
		final var permit = permit(1).season(seasonStart, seasonEnd).period(periodStart, periodEnd).build();

		assertThat(PermitPeriods.of(permit)).isEqualTo(expected);
	}
}
