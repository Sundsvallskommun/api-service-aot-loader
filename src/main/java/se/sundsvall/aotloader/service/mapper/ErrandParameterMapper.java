package se.sundsvall.aotloader.service.mapper;

import java.util.List;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.Constants.ERRAND_PARAMETER_MAX_LENGTH;

/**
 * Maps the parameters of a candidate to those of SupportManagement, of the errand and of its stakeholders.
 */
final class ErrandParameterMapper {

	private ErrandParameterMapper() {}

	static List<generated.se.sundsvall.supportmanagement.Parameter> toParameters(final List<Parameter> parameters) {
		return parameters.stream().map(ErrandParameterMapper::toParameter).toList();
	}

	/**
	 * A value longer than SupportManagement takes is split into several values (section 7.3.3).
	 */
	static generated.se.sundsvall.supportmanagement.Parameter toParameter(final Parameter parameter) {
		return new generated.se.sundsvall.supportmanagement.Parameter()
			.key(parameter.key())
			.displayName(parameter.displayName())
			.group(parameter.group())
			.values(parameter.values().stream().flatMap(value -> Texts.split(value, ERRAND_PARAMETER_MAX_LENGTH).stream()).toList());
	}
}
