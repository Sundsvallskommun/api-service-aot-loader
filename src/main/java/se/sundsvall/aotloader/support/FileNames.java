package se.sundsvall.aotloader.support;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Reads and compares the file names of the sources. A name is compared in its composed form and without regard to
 * case, so that an å written as a letter and a combining mark, as a file copied from macOS may have it, matches the
 * letter.
 */
public final class FileNames {

	private FileNames() {}

	/**
	 * @return the file name of a reference: the part after the last slash or backslash, should the database hold a path;
	 *         null when it names no file
	 */
	public static String nameOf(final String reference) {
		final var trimmed = Texts.trimToNull(reference);
		if (trimmed == null) {
			return null;
		}
		final var name = Texts.trimToNull(trimmed.substring(Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\')) + 1));
		return name == null || ".".equals(name) || "..".equals(name) ? null : name;
	}

	/**
	 * @return the file name of a reference as it is compared; null when it names no file
	 */
	public static String key(final String reference) {
		return normalized(nameOf(reference));
	}

	/**
	 * @return the text composed and in lower case, for comparing names and finding words in them; null for null
	 */
	public static String normalized(final String text) {
		return text == null ? null : Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
	}
}
