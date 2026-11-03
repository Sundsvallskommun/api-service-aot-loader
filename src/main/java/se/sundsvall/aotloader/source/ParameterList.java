package se.sundsvall.aotloader.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.support.Texts;

/**
 * Collects parameters and leaves out those without a value (section 7.3.3: empty values are left out).
 */
public final class ParameterList {

	private final List<Parameter> parameters = new ArrayList<>();

	/**
	 * Adds the parameter when the value is not blank, with the value stripped.
	 */
	public ParameterList add(final String key, final String displayName, final String value) {
		Optional.ofNullable(Texts.trimToNull(value)).ifPresent(nonBlank -> parameters.add(new Parameter(key, displayName, List.of(nonBlank))));
		return this;
	}

	public List<Parameter> build() {
		return List.copyOf(parameters);
	}
}
