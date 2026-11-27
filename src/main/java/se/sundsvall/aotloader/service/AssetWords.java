package se.sundsvall.aotloader.service;

import se.sundsvall.aotloader.service.model.Kind;

/**
 * The words the report uses for the post of an item in PartyAssets, a permit or a notification.
 *
 * @param definite   the post in the definite form, as "tillståndet"
 * @param genitive   the definite form in the genitive, as "tillståndets"
 * @param pronoun    the pronoun that refers to the post, as "det"
 * @param indefinite the post in the indefinite form, as "ett tillstånd"
 */
record AssetWords(String definite, String genitive, String pronoun, String indefinite) {

	static final AssetWords PERMIT = new AssetWords("tillståndet", "tillståndets", "det", "ett tillstånd");
	static final AssetWords NOTIFICATION = new AssetWords("anmälan", "anmälans", "den", "en anmälan");

	/**
	 * @return the words for the post of the kind; a notification's, or else a permit's
	 */
	static AssetWords of(final Kind kind) {
		return kind == Kind.ANMALAN ? NOTIFICATION : PERMIT;
	}

	/**
	 * @return the definite form with a capital, as "Tillståndet"
	 */
	String capitalized() {
		return capitalize(definite);
	}

	/**
	 * @return the genitive with a capital, as "Tillståndets"
	 */
	String capitalizedGenitive() {
		return capitalize(genitive);
	}

	private static String capitalize(final String word) {
		return Character.toUpperCase(word.charAt(0)) + word.substring(1);
	}
}
