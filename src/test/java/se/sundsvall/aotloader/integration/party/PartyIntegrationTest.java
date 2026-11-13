package se.sundsvall.aotloader.integration.party;

import feign.Request;
import feign.RetryableException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.aotloader.service.TestData;
import se.sundsvall.aotloader.support.MigrationException;
import se.sundsvall.aotloader.support.SystemUnavailableException;
import se.sundsvall.dept44.problem.Problem;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.GATEWAY_TIMEOUT;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

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
			.isNotInstanceOf(SystemUnavailableException.class)
			.hasMessage("Uppslaget i Party misslyckades (IllegalStateException)");
	}

	@Test
	void aPartyThatCouldNotBeReachedIsToldApartFromAnAnswer() {
		final var integration = new PartyIntegration(client);
		final var request = Request.create(Request.HttpMethod.GET, "/2281/PRIVATE/198001011234/partyId", Map.of(), null, UTF_8, null);
		when(client.getPartyId(MUNICIPALITY_ID, "PRIVATE", "198001011234"))
			.thenThrow(new RetryableException(-1, "Read timed out executing GET /2281/PRIVATE/198001011234/partyId", Request.HttpMethod.GET, new SocketTimeoutException(), (Long) null, request))
			.thenThrow(Problem.valueOf(SERVICE_UNAVAILABLE, "Fel vid anrop till /2281/PRIVATE/198001011234/partyId"))
			.thenThrow(Problem.valueOf(GATEWAY_TIMEOUT, "Fel"))
			.thenThrow(Problem.valueOf(TOO_MANY_REQUESTS, "Fel"));

		assertThatThrownBy(() -> integration.getPartyId(MUNICIPALITY_ID, TestData.PERSON_ID))
			.isInstanceOf(SystemUnavailableException.class)
			.hasMessage("Party gick inte att nå (RetryableException); uppslaget görs om i nästa körning");
		assertThatThrownBy(() -> integration.getPartyId(MUNICIPALITY_ID, TestData.PERSON_ID))
			.isInstanceOf(SystemUnavailableException.class)
			.hasMessage("Party gick inte att nå (503 SERVICE_UNAVAILABLE); uppslaget görs om i nästa körning");
		assertThatThrownBy(() -> integration.getPartyId(MUNICIPALITY_ID, TestData.PERSON_ID)).isInstanceOf(SystemUnavailableException.class);
		assertThatThrownBy(() -> integration.getPartyId(MUNICIPALITY_ID, TestData.PERSON_ID)).isInstanceOf(SystemUnavailableException.class);
	}
}
