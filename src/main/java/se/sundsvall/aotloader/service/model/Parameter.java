package se.sundsvall.aotloader.service.model;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A parameter of an errand or a stakeholder.
 *
 * @param key         the key
 * @param displayName the name shown to the case handler, null for none
 * @param values      the values, in order
 */
public record Parameter(String key, String displayName, List<String> values) {

	public Parameter {
		requireNonNull(key, "key");
		values = values == null ? List.of() : List.copyOf(values);
	}
}
