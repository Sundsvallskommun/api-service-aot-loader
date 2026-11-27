package se.sundsvall.aotloader.integration.party;

import java.util.Optional;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import se.sundsvall.aotloader.integration.party.configuration.PartyConfiguration;

import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;
import static se.sundsvall.aotloader.integration.party.configuration.PartyConfiguration.CLIENT_ID;

@FeignClient(name = CLIENT_ID, url = "${integration.party.url}", configuration = PartyConfiguration.class, dismiss404 = true)
public interface PartyClient {

	/**
	 * @param  type    ENTERPRISE or PRIVATE
	 * @param  legalId the organization number or personal identity number
	 * @return         the partyId, empty when Party does not know the number
	 */
	@GetMapping(path = "/{municipalityId}/{type}/{legalId}/partyId", produces = TEXT_PLAIN_VALUE)
	Optional<String> getPartyId(
		@PathVariable String municipalityId,
		@PathVariable String type,
		@PathVariable String legalId);
}
