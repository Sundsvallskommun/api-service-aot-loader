package se.sundsvall.aotloader.service.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PartyTypeTest {

	// Party knows an organization number as ENTERPRISE, while the stakeholder in SupportManagement is a COMPANY
	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
		ENTERPRISE | COMPANY
		PRIVATE    | PRIVATE
		""")
	void theExternalIdTypeOfAStakeholder(final PartyType type, final String externalIdType) {
		assertThat(type.getExternalIdType()).isEqualTo(externalIdType);
	}
}
