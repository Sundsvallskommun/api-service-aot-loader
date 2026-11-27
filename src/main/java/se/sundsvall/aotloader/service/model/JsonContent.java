package se.sundsvall.aotloader.service.model;

import tools.jackson.databind.node.ObjectNode;

import static java.util.Objects.requireNonNull;

/**
 * The value of a JSON parameter the migration writes, with the type that gives it its key and schema (decision 92).
 * The value may name persons, and {@link #toString()} shows only its fields.
 *
 * @param type  the type of the parameter
 * @param value the value, as the schema of the type wants it
 */
public record JsonContent(JsonParameterType type, ObjectNode value) {

	public JsonContent {
		requireNonNull(type, "type");
		requireNonNull(value, "value");
		value = value.deepCopy();
	}

	/**
	 * @return a copy of the value, which the caller may change
	 */
	@Override
	public ObjectNode value() {
		return value.deepCopy();
	}

	/**
	 * @return true when the value has no fields, and the parameter would say nothing
	 */
	public boolean isEmpty() {
		return value.isEmpty();
	}

	@Override
	public String toString() {
		return "JsonContent[type=" + type + ", value=" + value.propertyNames() + "]";
	}
}
