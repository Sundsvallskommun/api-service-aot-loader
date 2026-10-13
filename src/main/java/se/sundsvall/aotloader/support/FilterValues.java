package se.sundsvall.aotloader.support;

import java.util.regex.Pattern;

/**
 * Guards the values that go into the quoted filter expressions of the search APIs of the target systems.
 */
public final class FilterValues {

	private static final Pattern SAFE = Pattern.compile("[\\w\\-]+");

	private FilterValues() {}

	/**
	 * @return true for a value of only letters a–z, digits, underscores and hyphens, which cannot end the quote it is put
	 *         in; false for null
	 */
	public static boolean isSafe(final String value) {
		return value != null && SAFE.matcher(value).matches();
	}
}
