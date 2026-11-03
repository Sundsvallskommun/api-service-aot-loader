package se.sundsvall.aotloader.support;

import java.util.Comparator;
import tools.jackson.databind.JsonNode;

/**
 * Compares JSON values as the target systems give them back.
 */
public final class JsonValues {

	// A number read back may be of another type than the one sent, such as a long for an int
	private static final Comparator<JsonNode> NUMBERS_BY_VALUE = (one, other) -> {
		if (one.isNumber() && other.isNumber()) {
			return one.decimalValue().compareTo(other.decimalValue());
		}
		return one.equals(other) ? 0 : 1;
	};

	private JsonValues() {}

	/**
	 * @return true when both are null, or both have the same fields with the same values, whatever the order of the
	 *         fields and whatever type of number a value has
	 */
	public static boolean same(final JsonNode one, final JsonNode other) {
		return one == null ? other == null : other != null && one.equals(NUMBERS_BY_VALUE, other);
	}
}
