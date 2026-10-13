package se.sundsvall.aotloader.integration.partyassets;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import se.sundsvall.aotloader.integration.partyassets.configuration.PartyAssetsConfiguration;

import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static se.sundsvall.aotloader.integration.partyassets.configuration.PartyAssetsConfiguration.CLIENT_ID;

@FeignClient(name = CLIENT_ID, url = "${integration.party-assets.url}", configuration = PartyAssetsConfiguration.class)
public interface PartyAssetsClient {

	@GetMapping(path = "/{municipalityId}/assets", produces = APPLICATION_JSON_VALUE)
	List<Asset> getAssets(
		@PathVariable String municipalityId,
		@RequestParam("partyId") String partyId,
		@RequestParam("assetId") String assetId);

	/**
	 * @param sourceReference the relation PartyAssets writes from the permit to the errand
	 */
	@PostMapping(path = "/{municipalityId}/assets", consumes = APPLICATION_JSON_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> createAsset(
		@PathVariable String municipalityId,
		@RequestParam("sourceReference") String sourceReference,
		@RequestBody AssetCreateRequest asset);
}
