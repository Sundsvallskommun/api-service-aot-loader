package se.sundsvall.aotloader.support;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads and compares the file names of the sources, and names a file the migration puts on an errand under a name of
 * its own. A name is compared in its composed form and without regard to case, so that an å written as a letter and a
 * combining mark, as a file copied from macOS may have it, matches the letter.
 */
public final class FileNames {

	// What a file name cannot hold when it is saved on Windows, as a downloaded attachment is
	private static final Pattern NOT_IN_A_NAME = Pattern.compile("[/\\\\:*?<>|]");
	private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");
	private static final Pattern BLANKS = Pattern.compile("[\\s\\p{Z}]+");

	private FileNames() {}

	/**
	 * Names a file put on an errand by its title and the case number it shows, with the extension of the file in the
	 * source (decision 110). In the case number, a slash, a backslash, a colon and the other characters a file name cannot
	 * hold become a dash, a quote is left out, a control character becomes a space, and blanks, a no-break space among
	 * them, become one space.
	 *
	 * @param  title      the title the name begins with
	 * @param  caseNumber the case number the file shows, null when it shows none, which leaves the title alone
	 * @param  sourceName the name of the file in the source
	 * @return            the name, such as "Tillståndsbevis MBN-2019-123.rtf"
	 */
	public static String caseFileName(final String title, final String caseNumber, final String sourceName) {
		final var number = Optional.ofNullable(caseNumber)
			.map(value -> CONTROL_CHARACTERS.matcher(value).replaceAll(" "))
			.map(value -> NOT_IN_A_NAME.matcher(value).replaceAll("-").replace("\"", ""))
			.map(value -> BLANKS.matcher(value).replaceAll(" ").strip())
			.filter(value -> !value.isEmpty());
		final var name = nameOf(sourceName);
		final var dot = name == null ? -1 : name.lastIndexOf('.');
		final var extension = dot > 0 ? name.substring(dot) : "";
		return title + number.map(value -> " " + value).orElse("") + extension;
	}

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
