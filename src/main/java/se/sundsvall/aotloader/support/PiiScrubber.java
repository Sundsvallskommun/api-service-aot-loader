package se.sundsvall.aotloader.support;

import java.util.regex.Pattern;

/**
 * Masks what looks like personal identity numbers, organization numbers and email addresses in texts that go to the
 * status table, the report or the log (K6). It is a safety net for messages the migration does not write itself, such
 * as errors from the target systems; it cannot find names.
 */
public final class PiiScrubber {

	// Bounded by anything but a digit, so that a number written next to a word or a hyphen, as in a file name, is found,
	// and with up to three characters of any kind between its parts, as in 700101_1234 or "700101 - 1234"
	private static final Pattern LEGAL_ID = Pattern.compile("(?<!\\d)(\\d{2})?\\d{6}[^\\p{Alnum}]{0,3}\\d{4}(?!\\d)");
	private static final Pattern UUID = Pattern.compile("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}");
	// A domain with at least one dot, matched without a repeated group, whose recursion could overflow the stack. It
	// does not end with a dot, which is the full stop of a sentence
	private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]*[\\w-]");
	private static final String MASKED_NUMBER = "[nummer]";

	private PiiScrubber() {}

	/**
	 * @return the text with numbers and email addresses masked, or null for null
	 */
	public static String scrub(final String text) {
		if (text == null) {
			return null;
		}
		return EMAIL.matcher(maskNumbers(text)).replaceAll("[e-post]");
	}

	/**
	 * Masks the numbers outside the UUIDs. A UUID is an id in a target system, and its groups of digits are kept.
	 */
	private static String maskNumbers(final String text) {
		final var result = new StringBuilder();
		final var uuids = UUID.matcher(text);
		var position = 0;
		while (uuids.find()) {
			result.append(LEGAL_ID.matcher(text.substring(position, uuids.start())).replaceAll(MASKED_NUMBER)).append(uuids.group());
			position = uuids.end();
		}
		return result.append(LEGAL_ID.matcher(text.substring(position)).replaceAll(MASKED_NUMBER)).toString();
	}
}
