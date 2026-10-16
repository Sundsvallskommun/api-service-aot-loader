package se.sundsvall.aotloader.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.support.Texts;

/**
 * Collects parameters and leaves out those without values (section 7.3.3: empty values are left out).
 */
public final class ParameterList {

	private final List<Parameter> parameters = new ArrayList<>();

	public ParameterList add(final String key, final String displayName, final String value) {
		return add(key, displayName, value == null ? List.of() : List.of(value));
	}

	public ParameterList add(final String key, final String displayName, final List<String> values) {
		final var nonBlank = values.stream().filter(Objects::nonNull).map(Texts::trimToNull).filter(Objects::nonNull).toList();
		if (!nonBlank.isEmpty()) {
			parameters.add(new Parameter(key, displayName, nonBlank));
		}
		return this;
	}

	public ParameterList add(final Parameter parameter) {
		return add(parameter.key(), parameter.displayName(), parameter.values());
	}

	public List<Parameter> build() {
		return List.copyOf(parameters);
	}
}
