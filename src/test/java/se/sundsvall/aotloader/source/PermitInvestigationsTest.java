package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.service.model.InvestigationData;
import se.sundsvall.aotloader.service.model.InvestigationTexts;

import static org.assertj.core.api.Assertions.assertThat;

class PermitInvestigationsTest {

	@Test
	void aPermitWithoutADecisionDateIsCompletedWhenTheLastErrandWasClosed() {
		final var texts = new InvestigationTexts("Noteringar", null, null);

		final var investigation = PermitInvestigations.of("Tillstånd", Arrays.asList(LocalDate.of(2019, 3, 1), null, LocalDate.of(2019, 2, 1)),
			Arrays.asList(LocalDate.of(2019, 6, 1), null, LocalDate.of(2019, 7, 1)), null, texts);

		assertThat(investigation).isEqualTo(new InvestigationData("Tillstånd", LocalDateTime.of(2019, 2, 1, 0, 0), LocalDateTime.of(2019, 7, 1, 0, 0), texts));
	}

	@Test
	void theDecisionDateCompletesTheInvestigation() {
		final var investigation = PermitInvestigations.of("Tillstånd", List.of(LocalDate.of(2019, 3, 1)), List.of(LocalDate.of(2019, 7, 1)), LocalDate.of(2019, 6, 20),
			InvestigationTexts.NONE);

		assertThat(investigation.completedAt()).isEqualTo(LocalDateTime.of(2019, 6, 20, 0, 0));
	}

	@Test
	void aPermitWithoutErrandsOrIssueDateHasNoDates() {
		assertThat(PermitInvestigations.of("Tillstånd", List.of(), List.of(), null, null)).isEqualTo(new InvestigationData("Tillstånd", null, null));
	}
}
