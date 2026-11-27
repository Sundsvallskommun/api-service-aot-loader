package se.sundsvall.aotloader.support;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Compares diary numbers the way the analysis did: without blanks and dashes, and without regard to case. Parts a diary
 * number from the reference to a delegation decision that Timrå writes after it.
 */
public final class DiaryNumbers {

	// Blanks and dashes of every kind, the no-break space and the en dash among them
	private static final Pattern SEPARATORS = Pattern.compile("[\\h\\v\\p{Pd}\\u2212]");
	// The word DEL followed by a year, as in "DEL/2019 §45" and "DEL2019 §45"
	private static final Pattern DELEGATION = Pattern.compile("\\bDEL(?=\\s*+/?\\s*+\\d)");
	private static final Pattern WORD_OF_LETTERS = Pattern.compile("\\p{L}++");

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

	/**
	 * Parts the diary number from the reference to a delegation decision written after it, as in "MBN/2019:123 MBN DEL/2019
	 * §45". The reference starts at the word DEL followed by a year, or at the word of letters before it, such as the name
	 * of the board.
	 *
	 * @return the diary number and the reference; the diary number as it is and no reference when it has none, or when
	 *         what comes before it has no digit
	 */
	public static Split split(final String diaryNumber) {
		if (diaryNumber == null) {
			return new Split(null, null);
		}
		final var matcher = DELEGATION.matcher(diaryNumber);
		if (!matcher.find()) {
			return new Split(diaryNumber, null);
		}
		final var before = diaryNumber.substring(0, matcher.start()).stripTrailing();
		final var lastWord = before.substring(lastBlank(before) + 1);
		final var start = lastWord.length() < before.length() && WORD_OF_LETTERS.matcher(lastWord).matches() ? before.length() - lastWord.length() : matcher.start();
		final var number = diaryNumber.substring(0, start).strip();
		if (number.chars().noneMatch(Character::isDigit)) {
			return new Split(diaryNumber, null);
		}
		return new Split(number, diaryNumber.substring(start).strip());
	}

	private static int lastBlank(final String text) {
		for (var i = text.length() - 1; i >= 0; i--) {
			if (Character.isWhitespace(text.charAt(i))) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * A diary number parted from the reference to a delegation decision written after it.
	 *
	 * @param number              the diary number, null when there is none
	 * @param delegationReference the reference to the delegation decision, null when there is none
	 */
	public record Split(String number, String delegationReference) {
	}
}
