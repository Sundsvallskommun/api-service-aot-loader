package se.sundsvall.aotloader.source.alkt;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PermitRow;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

/**
 * The season and the date period of a permit, interpreted as section 7.5 says.
 *
 * @param seasonStart the first day of the season, null when there is none
 * @param seasonEnd   the last day of the season
 * @param periodStart the first day of the date period, null when it has none
 * @param periodEnd   the last day of the date period, null when it has none
 * @param rawSeason   the season as the source has it, when it cannot be interpreted
 * @param rawPeriod   the period as the source has it, when it cannot be interpreted
 * @param problems    what could not be interpreted, for rule G9
 */
record PermitPeriods(MonthDay seasonStart, MonthDay seasonEnd, LocalDate periodStart, LocalDate periodEnd, String rawSeason, String rawPeriod, List<String> problems) {

	static PermitPeriods of(final PermitRow permit) {
		final var problems = new ArrayList<String>();
		MonthDay seasonStart = null;
		MonthDay seasonEnd = null;
		String rawSeason = null;
		if (permit.arligPeriodStart() != null || permit.arligPeriodSlut() != null) {
			final var start = SourceValues.parseDayOfYear(permit.arligPeriodStart());
			final var end = SourceValues.parseDayOfYear(permit.arligPeriodSlut());
			if (start.isPresent() && end.isPresent()) {
				seasonStart = start.get();
				seasonEnd = end.get();
			} else {
				rawSeason = Texts.range(permit.arligPeriodStart(), permit.arligPeriodSlut());
				problems.add("säsong '" + rawSeason + "'");
			}
		}
		LocalDate periodStart = null;
		LocalDate periodEnd = null;
		String rawPeriod = null;
		if (permit.periodStart() != null || permit.periodSlut() != null) {
			final var startDate = SourceValues.parseDate(permit.periodStart());
			final var endDate = SourceValues.parseDate(permit.periodSlut());
			final var datesOk = (permit.periodStart() == null || startDate.isPresent()) && (permit.periodSlut() == null || endDate.isPresent());
			final var startDay = SourceValues.parseDayOfYear(permit.periodStart());
			final var endDay = SourceValues.parseDayOfYear(permit.periodSlut());
			if (datesOk) {
				periodStart = startDate.orElse(null);
				periodEnd = endDate.orElse(null);
				final var period = Texts.range(permit.periodStart(), permit.periodSlut());
				if (periodStart == null || periodEnd == null) {
					// A period open at one end would make a temporary permit that never ends, or one without a start
					problems.add("perioden '%s' saknar %s".formatted(period, periodStart == null ? "början" : "slut"));
				} else if (periodEnd.isBefore(periodStart)) {
					problems.add("perioden '%s' slutar före sin början".formatted(period));
				}
			} else if (startDay.isPresent() && endDay.isPresent() && (seasonStart == null || (seasonStart.equals(startDay.get()) && seasonEnd.equals(endDay.get())))) {
				// A period of days and months is a season (section 7.3.3)
				seasonStart = startDay.get();
				seasonEnd = endDay.get();
			} else {
				rawPeriod = Texts.range(permit.periodStart(), permit.periodSlut());
				problems.add("period '" + rawPeriod + "'");
			}
		}
		return new PermitPeriods(seasonStart, seasonEnd, periodStart, periodEnd, rawSeason, rawPeriod, problems);
	}

	boolean hasDatePeriod() {
		return periodStart != null || periodEnd != null;
	}

	String seasonText() {
		if (seasonStart != null) {
			return Texts.range(SourceValues.format(seasonStart), SourceValues.format(seasonEnd));
		}
		return rawSeason;
	}

	String periodText() {
		if (hasDatePeriod()) {
			return Texts.range(SourceValues.format(periodStart), SourceValues.format(periodEnd));
		}
		return rawPeriod;
	}
}
