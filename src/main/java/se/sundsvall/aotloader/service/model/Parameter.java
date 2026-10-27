package se.sundsvall.aotloader.service.model;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A parameter of an errand or a stakeholder.
 *
 * @param key         the key
 * @param displayName the name shown to the case handler, null for none; for a table, its columns separated by "|"
 * @param group       the heading the case handler sees the parameter under, null for none
 * @param values      the values, in order; for a table, one row each, its cells separated by "|"
 */
public record Parameter(String key, String displayName, String group, List<String> values) {

	public Parameter {
		requireNonNull(key, "key");
		values = values == null ? List.of() : List.copyOf(values);
	}

	/**
	 * A parameter outside any group.
	 */
	public Parameter(final String key, final String displayName, final List<String> values) {
		this(key, displayName, null, values);
	}
}
