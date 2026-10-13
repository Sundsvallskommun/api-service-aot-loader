package se.sundsvall.aotloader.integration.partyassets;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.partyassets.Status;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import se.sundsvall.aotloader.support.MigrationException;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class PartyAssetsIntegrationTest {

	private static final String MUNICIPALITY_ID = "2281";

	@Mock
	private PartyAssetsClient client;

	@Test
	void findAsset() {
		final var integration = new PartyAssetsIntegration(client);
		when(client.getAssets(MUNICIPALITY_ID, "party", "asset")).thenReturn(List.of(new Asset().id("1").assetId("asset"), new Asset().id("2").assetId("other")));
		when(client.getAssets(MUNICIPALITY_ID, "party", "none")).thenReturn(null);
		when(client.getAssets(MUNICIPALITY_ID, "party", "twice")).thenReturn(List.of(new Asset().id("1").assetId("twice"), new Asset().id("2").assetId("twice")));

		assertThat(integration.findAsset(MUNICIPALITY_ID, "party", "asset")).map(Asset::getId).contains("1");
		assertThat(integration.findAsset(MUNICIPALITY_ID, "party", "none")).isEmpty();
		assertThatThrownBy(() -> integration.findAsset(MUNICIPALITY_ID, "party", "twice"))
			.isInstanceOf(MigrationException.class)
			.hasMessage("PartyAssets har 2 poster med samma tillstånds-id");
	}

	@Test
	void getAsset() {
		final var integration = new PartyAssetsIntegration(client);
		when(client.getAsset(MUNICIPALITY_ID, "1")).thenReturn(new Asset().id("1"));
		when(client.getAsset(MUNICIPALITY_ID, "gone")).thenThrow(Problem.valueOf(NOT_FOUND, "Finns inte"));
		when(client.getAsset(MUNICIPALITY_ID, "error")).thenThrow(Problem.valueOf(BAD_GATEWAY, "Fel"));

		assertThat(integration.getAsset(MUNICIPALITY_ID, "1")).map(Asset::getId).contains("1");
		assertThat(integration.getAsset(MUNICIPALITY_ID, "gone")).isEmpty();
		assertThatThrownBy(() -> integration.getAsset(MUNICIPALITY_ID, "error")).isInstanceOf(ThrowableProblem.class);
	}

	@Test
	void updateAsset() {
		final var request = new AssetUpdateRequest().status(Status.EXPIRED).title("Titel");

		new PartyAssetsIntegration(client).updateAsset(MUNICIPALITY_ID, "1", request);

		verify(client).updateAsset(MUNICIPALITY_ID, "1", request);
	}

	@Test
	void createAsset() {
		final var integration = new PartyAssetsIntegration(client);
		final var request = new AssetCreateRequest();
		when(client.createAsset(MUNICIPALITY_ID, "ref", request))
			.thenReturn(ResponseEntity.created(URI.create("/2281/assets/internal")).build())
			.thenThrow(Problem.valueOf(CONFLICT, "Finns redan"))
			.thenThrow(Problem.valueOf(BAD_GATEWAY, "Fel"));

		assertThat(integration.createAsset(MUNICIPALITY_ID, "ref", request)).contains("internal");
		assertThat(integration.createAsset(MUNICIPALITY_ID, "ref", request)).isEmpty();
		assertThatThrownBy(() -> integration.createAsset(MUNICIPALITY_ID, "ref", request)).isInstanceOf(ThrowableProblem.class);
	}
}
