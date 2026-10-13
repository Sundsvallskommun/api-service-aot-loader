package se.sundsvall.aotloader.service.model;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTest {

	@Test
	void partyIds() {
		final var ids = new PartyIds("holder", Arrays.asList("person", null));

		assertThat(ids.holderPartyId()).contains("holder");
		assertThat(ids.personPartyId(0)).contains("person");
		assertThat(ids.personPartyId(1)).isEmpty();
		assertThat(ids.personPartyId(2)).isEmpty();
		assertThat(new PartyIds(null, null).holderPartyId()).isEmpty();
		assertThat(new PartyIds(null, null).persons()).isEmpty();
	}

	@Test
	void theStepsOfTheFlowAreProgress() {
		assertThat(Arrays.stream(ItemStatus.values()).filter(ItemStatus::isProgress).toList()).isEqualTo(List.of(
			ItemStatus.TILLSTANDSHAVARE_KLAR, ItemStatus.ARENDE_SKAPAT, ItemStatus.BILAGOR_KLARA, ItemStatus.TILLSTAND_SKAPAT, ItemStatus.RELATION_KLAR, ItemStatus.KLAR));
	}
}
