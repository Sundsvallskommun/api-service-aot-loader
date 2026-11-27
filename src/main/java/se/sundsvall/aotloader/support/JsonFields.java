package se.sundsvall.aotloader.support;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds a JSON object field by field and leaves out a field without a value, as the AoT schemas of PartyAssets want
 * it: a missing value is left out, never sent as null. A blank text, an empty list and an object without fields have no
 * value. Texts are stripped.
 */
public final class JsonFields {

	private final ObjectNode node = JsonNodeFactory.instance.objectNode();

	private JsonFields() {}

	public static JsonFields object() {
		return new JsonFields();
	}

	public JsonFields text(final String key, final String value) {
		final var stripped = Texts.trimToNull(value);
		if (stripped != null) {
			node.put(key, stripped);
		}
		return this;
	}

	/**
	 * Adds the date as yyyy-MM-dd.
	 */
	public JsonFields date(final String key, final LocalDate value) {
		return text(key, SourceValues.format(value));
	}

	public JsonFields flag(final String key, final Boolean value) {
		if (value != null) {
			node.put(key, value);
		}
		return this;
	}

	public JsonFields number(final String key, final Integer value) {
		if (value != null) {
			node.put(key, value);
		}
		return this;
	}

	/**
	 * Adds the texts that are not blank, stripped, in their order.
	 */
	public JsonFields texts(final String key, final Collection<String> values) {
		final var stripped = values == null ? List.<String>of() : values.stream().map(Texts::trimToNull).filter(Objects::nonNull).toList();
		if (!stripped.isEmpty()) {
			final var array = node.putArray(key);
			stripped.forEach(array::add);
		}
		return this;
	}

	public JsonFields object(final String key, final JsonFields value) {
		if (value != null && !value.isEmpty()) {
			node.set(key, value.toNode());
		}
		return this;
	}

	/**
	 * Adds the objects that have fields, in their order.
	 */
	public JsonFields objects(final String key, final Collection<JsonFields> values) {
		final var withFields = values == null ? List.<JsonFields>of() : values.stream().filter(Objects::nonNull).filter(value -> !value.isEmpty()).toList();
		if (!withFields.isEmpty()) {
			final var array = node.putArray(key);
			withFields.forEach(value -> array.add(value.toNode()));
		}
		return this;
	}

	public boolean isEmpty() {
		return node.isEmpty();
	}

	/**
	 * @return the object as it is built so far, a copy that does not change with this builder
	 */
	public ObjectNode toNode() {
		return node.deepCopy();
	}

	/**
	 * Names the fields but shows none of their values, which may be personal data.
	 */
	@Override
	public String toString() {
		return "JsonFields" + node.propertyNames();
	}
}
