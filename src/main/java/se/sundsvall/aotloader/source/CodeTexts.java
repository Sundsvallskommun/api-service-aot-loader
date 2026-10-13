package se.sundsvall.aotloader.source;

import java.util.Map;
import java.util.Optional;

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
}
