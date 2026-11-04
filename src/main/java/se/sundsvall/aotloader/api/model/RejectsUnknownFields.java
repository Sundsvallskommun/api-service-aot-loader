package se.sundsvall.aotloader.api.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * A request body that refuses, with 400, a field it does not have. A misspelt field is then an error instead of a field
 * left out, which for a run would mean no limit or every source.
 */
public interface RejectsUnknownFields {

	/**
	 * Refuses a field of the body that the request does not have. Jackson calls it for every such field.
	 *
	 * @param  name                     the name of the field
	 * @param  value                    the value of the field
	 * @throws IllegalArgumentException always
	 */
	@JsonAnySetter
	default void unknownField(final String name, final Object value) {
		// The global setting of dept44 ignores unknown fields, and @JsonIgnoreProperties cannot turn that around
		throw new IllegalArgumentException("Okänt fält: " + name);
	}
}
