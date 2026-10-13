package se.sundsvall.aotloader.source;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

	public static LocalDate date(final ResultSet rs, final String column) throws SQLException {
		final var timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toLocalDateTime().toLocalDate();
	}

	public static LocalDateTime dateTime(final ResultSet rs, final String column) throws SQLException {
		final var timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toLocalDateTime();
	}

	public static boolean flag(final ResultSet rs, final String column) throws SQLException {
		return rs.getBoolean(column);
	}

	public static Integer integer(final ResultSet rs, final String column) throws SQLException {
		final var value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	/**
	 * Interprets a column whose type is not known, e.g. a column of a view whose definition cannot be read: a bit or a
	 * number other than 0, a date, or a text other than blank, "0" or "false" is set.
	 */
	public static boolean isSet(final ResultSet rs, final String column) throws SQLException {
		final var value = rs.getObject(column);
		return switch (value) {
			case null -> false;
			case final Boolean b -> b;
			case final Number n -> n.doubleValue() != 0;
			case final String s -> {
				final var trimmed = s.strip().toLowerCase(Locale.ROOT);
				yield !trimmed.isEmpty() && !"0".equals(trimmed) && !"false".equals(trimmed);
			}
			default -> true;
		};
	}

	/**
	 * @return the rows by their key, in the order read, the first row winning; rows without key are left out
	 */
	public static <T> Map<Integer, T> byKey(final List<T> rows, final Function<T, Integer> key) {
		return rows.stream()
			.filter(row -> key.apply(row) != null)
			.collect(toMap(key, row -> row, (first, second) -> first, LinkedHashMap::new));
	}
}
