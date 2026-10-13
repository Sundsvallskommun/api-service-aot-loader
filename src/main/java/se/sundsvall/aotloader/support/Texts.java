package se.sundsvall.aotloader.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Text handling shared by the readers and the mappers (section 7.5: blanks at the start and end are removed and empty
 * values are left out).
 */
public final class Texts {

	private static final String ELLIPSIS = "…";

	private Texts() {}

	/**
	 * @return the value without blanks at the start and the end, or null when nothing remains
	 */
	public static String trimToNull(final String value) {
		if (value == null) {
			return null;
		}
		final var trimmed = value.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/**
	 * @return true when the value is null or blank
	 */
	public static boolean isBlank(final String value) {
		return trimToNull(value) == null;
	}

	/**
	 * Shortens a value to a maximum length. A shortened value ends with the suffix, or with an ellipsis when the suffix is
	 * null.
	 *
	 * @param  value     the value, may be null
	 * @param  maxLength the maximum length, longer than the suffix
	 * @param  suffix    what a shortened value ends with
	 * @return           the value, shortened when needed
	 */
	public static String truncate(final String value, final int maxLength, final String suffix) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		final var end = suffix == null ? ELLIPSIS : suffix;
		if (end.length() >= maxLength) {
			throw new IllegalArgumentException("The suffix must be shorter than the maximum length");
		}
		return value.substring(0, maxLength - end.length()).stripTrailing() + end;
	}

	/**
	 * Splits a value into parts of at most the maximum length, at a blank when there is one in the second half of a part.
	 *
	 * @param  value     the value, may be null
	 * @param  maxLength the maximum length of a part
	 * @return           the parts, empty for a blank value
	 */
	public static List<String> split(final String value, final int maxLength) {
		final var remaining = trimToNull(value);
		final var parts = new ArrayList<String>();
		if (remaining == null) {
			return parts;
		}
		var rest = remaining;
		while (rest.length() > maxLength) {
			var cut = rest.lastIndexOf(' ', maxLength);
			if (cut < maxLength / 2) {
				cut = maxLength;
			}
			parts.add(rest.substring(0, cut).strip());
			rest = rest.substring(cut).strip();
		}
		if (!rest.isEmpty()) {
			parts.add(rest);
		}
		return parts;
	}

	/**
	 * @return the non-blank values, trimmed, joined with the delimiter, or null when there are none
	 */
	public static String join(final String delimiter, final Collection<String> values) {
		final var joined = String.join(delimiter, values.stream().map(Texts::trimToNull).filter(Objects::nonNull).toList());
		return joined.isEmpty() ? null : joined;
	}

	/**
	 * @return the non-blank values, trimmed, joined with a blank, or null when there are none
	 */
	public static String joinWords(final String... values) {
		return join(" ", Arrays.asList(values));
	}
}
