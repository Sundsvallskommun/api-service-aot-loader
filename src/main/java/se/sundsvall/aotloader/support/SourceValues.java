package se.sundsvall.aotloader.support;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static java.util.Map.entry;

/**
 * Interprets the free text dates, seasons and serving times of the source databases (section 7.5).
 */
public final class SourceValues {

	private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})(?:[ T]\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?)?");
	private static final Pattern COMPACT_DATE = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})");
	private static final Pattern MONTH_DAY = Pattern.compile("(\\d{2})-(\\d{2})");
	private static final Pattern DAY_AND_MONTH_NAME = Pattern.compile("(\\d{1,2})\\s*([a-zåäö]+)\\.?");
	private static final Pattern TIME = Pattern.compile("(\\d{1,2})[:.](\\d{2})");

	private static final Map<String, Integer> MONTHS = Map.ofEntries(
		entry("jan", 1), entry("januari", 1),
		entry("feb", 2), entry("febr", 2), entry("februari", 2),
		entry("mar", 3), entry("mars", 3),
		entry("apr", 4), entry("april", 4),
		entry("maj", 5),
		entry("jun", 6), entry("juni", 6),
		entry("jul", 7), entry("juli", 7),
		entry("aug", 8), entry("augusti", 8),
		entry("sep", 9), entry("sept", 9), entry("september", 9),
		entry("okt", 10), entry("oktober", 10),
		entry("nov", 11), entry("november", 11),
		entry("dec", 12), entry("december", 12));

	private SourceValues() {}

	/**
	 * Interprets a date written as "ÅÅÅÅ-MM-DD", possibly followed by a time, or as "ÅÅÅÅMMDD".
	 *
	 * @return the date, or empty when the value is blank or is not such a date
	 */
	public static Optional<LocalDate> parseDate(final String value) {
		final var trimmed = Texts.trimToNull(value);
		if (trimmed == null) {
			return Optional.empty();
		}
		var matcher = ISO_DATE.matcher(trimmed);
		if (!matcher.matches()) {
			matcher = COMPACT_DATE.matcher(trimmed);
			if (!matcher.matches()) {
				return Optional.empty();
			}
		}
		try {
			return Optional.of(LocalDate.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))));
		} catch (final DateTimeException e) {
			return Optional.empty();
		}
	}

	/**
	 * Interprets a day of the year written as a day and a Swedish month in full or short form ("1 maj", "01 maj", "30 sep",
	 * "30 sept", "31 okt", "1 juni"), or as "MM-DD".
	 *
	 * @return the day of the year, or empty when the value is blank or cannot be interpreted
	 */
	public static Optional<MonthDay> parseDayOfYear(final String value) {
		final var trimmed = Texts.trimToNull(value);
		if (trimmed == null) {
			return Optional.empty();
		}
		final var lowerCase = trimmed.toLowerCase(Locale.ROOT);
		final var monthDay = MONTH_DAY.matcher(lowerCase);
		if (monthDay.matches()) {
			return monthDay(Integer.parseInt(monthDay.group(1)), Integer.parseInt(monthDay.group(2)));
		}
		final var dayAndMonthName = DAY_AND_MONTH_NAME.matcher(lowerCase);
		if (dayAndMonthName.matches() && MONTHS.containsKey(dayAndMonthName.group(2))) {
			return monthDay(MONTHS.get(dayAndMonthName.group(2)), Integer.parseInt(dayAndMonthName.group(1)));
		}
		return Optional.empty();
	}

	/**
	 * Interprets a time written as "hh:mm", "h:mm" or "hh.mm". 24:00 is a valid end of a day.
	 *
	 * @return the time as "hh:mm", or empty when the value is blank or cannot be interpreted
	 */
	public static Optional<String> parseTime(final String value) {
		final var trimmed = Texts.trimToNull(value);
		if (trimmed == null) {
			return Optional.empty();
		}
		final var matcher = TIME.matcher(trimmed);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		final var hours = Integer.parseInt(matcher.group(1));
		final var minutes = Integer.parseInt(matcher.group(2));
		if (minutes > 59 || hours > 24 || (hours == 24 && minutes > 0)) {
			return Optional.empty();
		}
		return Optional.of("%02d:%02d".formatted(hours, minutes));
	}

	/**
	 * @return the date as "ÅÅÅÅ-MM-DD", or null for null
	 */
	public static String format(final LocalDate date) {
		return date == null ? null : date.toString();
	}

	/**
	 * @return the day of the year as "MM-DD"
	 */
	public static String format(final MonthDay monthDay) {
		return "%02d-%02d".formatted(monthDay.getMonthValue(), monthDay.getDayOfMonth());
	}

	private static Optional<MonthDay> monthDay(final int month, final int day) {
		try {
			return Optional.of(MonthDay.of(month, day));
		} catch (final DateTimeException e) {
			return Optional.empty();
		}
	}
}
