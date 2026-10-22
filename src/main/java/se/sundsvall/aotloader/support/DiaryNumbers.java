package se.sundsvall.aotloader.support;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Compares diary numbers the way the analysis did: without blanks and dashes, and without regard to case.
 */
public final class DiaryNumbers {

	// Blanks and dashes of every kind, the no-break space and the en dash among them
	private static final Pattern SEPARATORS = Pattern.compile("[\\h\\v\\p{Pd}\\u2212]");

	private DiaryNumbers() {}

	/**
	 * @return the diary number in upper case without blanks and dashes, or null when nothing remains
	 */
	public static String normalize(final String diaryNumber) {
		if (diaryNumber == null) {
			return null;
		}
		final var normalized = SEPARATORS.matcher(diaryNumber).replaceAll("").toUpperCase(Locale.ROOT);
		return normalized.isEmpty() ? null : normalized;
	}
}
