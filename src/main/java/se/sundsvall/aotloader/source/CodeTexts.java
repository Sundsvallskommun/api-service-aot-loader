package se.sundsvall.aotloader.source;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import se.sundsvall.aotloader.support.Texts;

/**
 * Looks codes up in the code lists of the sources.
 */
public final class CodeTexts {

	private CodeTexts() {}

	/**
	 * Finds the text of a code. The databases compare codes without regard to case, and so does the lookup.
	 *
	 * @param  texts the code list, text by code
	 * @param  code  the code
	 * @return       the text of the code, or the code itself when the list does not have it; null for a null code
	 */
	public static String textOf(final Map<String, String> texts, final String code) {
		if (code == null) {
			return null;
		}
		return Optional.ofNullable(texts.get(code))
			.or(() -> texts.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(code)).map(Map.Entry::getValue).findFirst())
			.orElse(code);
	}

	/**
	 * @return true when the code, without the blanks around it, is one of the codes, regardless of case, as the databases
	 *         compare them; false for a blank code
	 */
	public static boolean isOneOf(final String code, final Collection<String> codes) {
		final var trimmed = Texts.trimToNull(code);
		return trimmed != null && codes.stream().anyMatch(trimmed::equalsIgnoreCase);
	}
}
