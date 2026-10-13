package se.sundsvall.aotloader.support;

import java.util.Locale;

/**
 * Compares diary numbers the way the analysis did: without blanks and hyphens, and without regard to case.
 */
public final class DiaryNumbers {

	private DiaryNumbers() {}

	/**
	 * @return the diary number in upper case without blanks and hyphens, or null when nothing remains
	 */
	public static String normalize(final String diaryNumber) {
		if (diaryNumber == null) {
			return null;
		}
		final var normalized = diaryNumber.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
		return normalized.isEmpty() ? null : normalized;
	}
}
