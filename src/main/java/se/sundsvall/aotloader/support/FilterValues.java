package se.sundsvall.aotloader.support;

import java.util.regex.Pattern;

/**
 * Guards the values that go into the quoted filter expressions of the search APIs of the target systems.
 */
public final class FilterValues {

	private static final Pattern SAFE = Pattern.compile("[\\w\\-]+");

	private FilterValues() {}

	/**
	 * @return true for a value of only letters a–z and A–Z, digits, underscores and hyphens, which cannot end the quote it
	 *         is put in; false for null
	 */
	public static boolean isSafe(final String value) {
		return value != null && SAFE.matcher(value).matches();
	}

	/**
	 * @param  value              the value
	 * @param  what               what the value is, for the message, e.g. "Ärendets id"
	 * @return                    the value in single quotes, for a filter expression
	 * @throws MigrationException when the value is not safe, as above
	 */
	public static String quoted(final String value, final String what) {
		if (!isSafe(value)) {
			throw new MigrationException("%s innehåller tecken som inte går att söka på".formatted(what));
		}
		return "'" + value + "'";
	}
}
