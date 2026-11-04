package se.sundsvall.aotloader.integration.party;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.aotloader.service.TestData;
import se.sundsvall.aotloader.support.MigrationException;
import se.sundsvall.dept44.problem.Problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@ExtendWith(MockitoExtension.class)
class PartyIntegrationTest {

	private static final String MUNICIPALITY_ID = "2281";

	@Mock
	private PartyClient client;

	@Test
	void getPartyId() {
		final var integration = new PartyIntegration(client);
		when(client.getPartyId(MUNICIPALITY_ID, "ENTERPRISE", "5566778899")).thenReturn(Optional.of("party-id")).thenReturn(Optional.of(" ")).thenReturn(Optional.empty());

		assertThat(integration.getPartyId(MUNICIPALITY_ID, TestData.HOLDER_ID)).contains("party-id");
		assertThat(integration.getPartyId(MUNICIPALITY_ID, TestData.HOLDER_ID)).isEmpty();
		assertThat(integration.getPartyId(MUNICIPALITY_ID, TestData.HOLDER_ID)).isEmpty();
	}

	@Test
	void anErrorNeverPassesOnTheNumber() {
		final var integration = new PartyIntegration(client);
		when(client.getPartyId(MUNICIPALITY_ID, "PRIVATE", "198001011234"))
			.thenThrow(Problem.valueOf(BAD_GATEWAY, "Fel vid anrop till /2281/PRIVATE/198001011234/partyId"))
			.thenThrow(new IllegalStateException("Fel vid anrop till /2281/PRIVATE/198001011234/partyId"));

		assertThatThrownBy(() -> integration.getPartyId(MUNICIPALITY_ID, TestData.PERSON_ID))
			.isInstanceOf(MigrationException.class)
			.hasMessage("Uppslaget i Party misslyckades (502 BAD_GATEWAY)");
		assertThatThrownBy(() -> integration.getPartyId(MUNICIPALITY_ID, TestData.PERSON_ID))
			.isInstanceOf(MigrationException.class)
			.hasMessage("Uppslaget i Party misslyckades (IllegalStateException)");
	}
}
