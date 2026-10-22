package se.sundsvall.aotloader.source;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import se.sundsvall.aotloader.support.Texts;

import static java.util.stream.Collectors.toMap;

/**
 * Reads the columns of the source databases the way section 7.5 says: text trimmed and empty text as null, dates as
 * they stand without time zone, and a missing bit as false.
 */
public final class Rows {

	private Rows() {}

	public static String text(final ResultSet rs, final String column) throws SQLException {
		return Texts.trimToNull(rs.getString(column));
	}

	/**
	 * @return the texts of the columns that are not blank, in the order of the columns
	 */
	public static List<String> texts(final ResultSet rs, final String... columns) throws SQLException {
		final var texts = new ArrayList<String>();
		for (final var column : columns) {
			Optional.ofNullable(text(rs, column)).ifPresent(texts::add);
		}
		return texts;
	}

	public static LocalDate date(final ResultSet rs, final String column) throws SQLException {
		final var dateTime = dateTime(rs, column);
		return dateTime == null ? null : dateTime.toLocalDate();
	}

	// Read as a local date and time, not as a Timestamp, whose way through the time zone of the JVM moves a time in the
	// hour skipped when summer time begins
	public static LocalDateTime dateTime(final ResultSet rs, final String column) throws SQLException {
		return rs.getObject(column, LocalDateTime.class);
	}

	public static boolean flag(final ResultSet rs, final String column) throws SQLException {
		return rs.getBoolean(column);
	}

	public static Integer integer(final ResultSet rs, final String column) throws SQLException {
		final var value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	/**
	 * Reads a bit strictly, e.g. a column of a view whose definition cannot be read: a bit, or a whole number 0 or 1. A
	 * missing value is not set.
	 *
	 * @return the value, or empty when the column holds anything else
	 */
	public static Optional<Boolean> bit(final ResultSet rs, final String column) throws SQLException {
		final var value = rs.getObject(column);
		return switch (value) {
			case null -> Optional.of(false);
			case final Boolean b -> Optional.of(b);
			case final Integer i when i == 0 || i == 1 -> Optional.of(i == 1);
			case final Short s when s == 0 || s == 1 -> Optional.of(s == 1);
			case final Byte b when b == 0 || b == 1 -> Optional.of(b == 1);
			case final Long l when l == 0 || l == 1 -> Optional.of(l == 1);
			default -> Optional.empty();
		};
	}

	/**
	 * @return the rows by their key, in the order read, the first row winning; rows without key are left out
	 */
	public static <T> Map<Integer, T> byKey(final List<T> rows, final Function<T, Integer> key) {
		return rows.stream()
			.filter(row -> key.apply(row) != null)
			.collect(toMap(key, row -> row, (first, _) -> first, LinkedHashMap::new));
	}
}
