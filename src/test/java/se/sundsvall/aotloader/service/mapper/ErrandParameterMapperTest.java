package se.sundsvall.aotloader.service.mapper;

import java.util.List;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.service.model.Parameter;

import static org.assertj.core.api.Assertions.assertThat;

class ErrandParameterMapperTest {

	@Test
	void aValueLongerThanSupportManagementTakesIsSplitIntoSeveralValues() {
		final var parameter = ErrandParameterMapper.toParameter(new Parameter("title", "Titel", List.of("a".repeat(3000) + " " + "b".repeat(10), "Revisor")));

		assertThat(parameter.getKey()).isEqualTo("title");
		assertThat(parameter.getDisplayName()).isEqualTo("Titel");
		assertThat(parameter.getValues()).containsExactly("a".repeat(3000), "b".repeat(10), "Revisor");
	}
}
