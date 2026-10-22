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
		final var season = Season.of(permit, problems);
		if (permit.periodStart() == null && permit.periodSlut() == null) {
			return new PermitPeriods(season.start(), season.end(), null, null, season.raw(), null, problems);
		}
		final var startDate = SourceValues.parseDate(permit.periodStart());
		final var endDate = SourceValues.parseDate(permit.periodSlut());
		if ((permit.periodStart() == null || startDate.isPresent()) && (permit.periodSlut() == null || endDate.isPresent())) {
			final var periodStart = startDate.orElse(null);
			final var periodEnd = endDate.orElse(null);
			checkDatePeriod(permit, periodStart, periodEnd, problems);
			return new PermitPeriods(season.start(), season.end(), periodStart, periodEnd, season.raw(), null, problems);
		}
		final var startDay = SourceValues.parseDayOfYear(permit.periodStart());
		final var endDay = SourceValues.parseDayOfYear(permit.periodSlut());
		if (startDay.isPresent() && endDay.isPresent() && season.allows(startDay.get(), endDay.get())) {
			// A period of days and months is a season (section 7.3.3)
			return new PermitPeriods(startDay.get(), endDay.get(), null, null, season.raw(), null, problems);
		}
		final var rawPeriod = Texts.dateRange(permit.periodStart(), permit.periodSlut());
		problems.add("period '" + rawPeriod + "'");
		return new PermitPeriods(season.start(), season.end(), null, null, season.raw(), rawPeriod, problems);
	}

	private static void checkDatePeriod(final PermitRow permit, final LocalDate periodStart, final LocalDate periodEnd, final List<String> problems) {
		final var period = Texts.dateRange(permit.periodStart(), permit.periodSlut());
		if (periodStart == null || periodEnd == null) {
			// A period open at one end would make a temporary permit that never ends, or one without a start
			problems.add("perioden '%s' saknar %s".formatted(period, periodStart == null ? "början" : "slut"));
		} else if (periodEnd.isBefore(periodStart)) {
			problems.add("perioden '%s' slutar före sin början".formatted(period));
		}
	}

	boolean hasDatePeriod() {
		return periodStart != null || periodEnd != null;
	}

	String seasonText() {
		if (seasonStart != null) {
			return Texts.dateRange(SourceValues.format(seasonStart), SourceValues.format(seasonEnd));
		}
		return rawSeason;
	}

	String periodText() {
		if (hasDatePeriod()) {
			return Texts.dateRange(SourceValues.format(periodStart), SourceValues.format(periodEnd));
		}
		return rawPeriod;
	}

	/**
	 * The season of a permit.
	 *
	 * @param start the first day, null when there is no season or it cannot be interpreted
	 * @param end   the last day, null when there is no season or it cannot be interpreted
	 * @param raw   the season as the source has it, when it cannot be interpreted
	 */
	private record Season(MonthDay start, MonthDay end, String raw) {

		static Season of(final PermitRow permit, final List<String> problems) {
			if (permit.arligPeriodStart() == null && permit.arligPeriodSlut() == null) {
				return new Season(null, null, null);
			}
			final var start = SourceValues.parseDayOfYear(permit.arligPeriodStart());
			final var end = SourceValues.parseDayOfYear(permit.arligPeriodSlut());
			if (start.isPresent() && end.isPresent()) {
				return new Season(start.get(), end.get(), null);
			}
			final var raw = Texts.dateRange(permit.arligPeriodStart(), permit.arligPeriodSlut());
			problems.add("säsong '" + raw + "'");
			return new Season(null, null, raw);
		}

		/**
		 * @return true when a period of days and months can stand for the season: there is no interpreted season, or it is
		 *         the same
		 */
		boolean allows(final MonthDay periodStart, final MonthDay periodEnd) {
			return start == null || (start.equals(periodStart) && end.equals(periodEnd));
		}
	}
}
