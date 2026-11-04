package se.sundsvall.aotloader.integration.partyassets;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetAttachment;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import se.sundsvall.aotloader.integration.partyassets.configuration.PartyAssetsConfiguration;

import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
import static se.sundsvall.aotloader.integration.partyassets.configuration.PartyAssetsConfiguration.CLIENT_ID;

@FeignClient(name = CLIENT_ID, url = "${integration.party-assets.url}", configuration = PartyAssetsConfiguration.class)
public interface PartyAssetsClient {

	/** The name of the part that holds the file of an attachment. */
	String ATTACHMENT_PART = "attachment";
	/** The name of the part that holds what the attachment depicts. */
	String CATEGORY_PART = "category";

	@GetMapping(path = "/{municipalityId}/assets", produces = APPLICATION_JSON_VALUE)
	List<Asset> getAssets(
		@PathVariable String municipalityId,
		@RequestParam("partyId") String partyId,
		@RequestParam("assetId") String assetId);

	@GetMapping(path = "/{municipalityId}/assets/{id}", produces = APPLICATION_JSON_VALUE)
	Asset getAsset(
		@PathVariable String municipalityId,
		@PathVariable String id);

	/**
	 * @param sourceReference the relation PartyAssets writes from the permit to the errand
	 */
	@PostMapping(path = "/{municipalityId}/assets", consumes = APPLICATION_JSON_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> createAsset(
		@PathVariable String municipalityId,
		@RequestParam("sourceReference") String sourceReference,
		@RequestBody AssetCreateRequest asset);

	@PatchMapping(path = "/{municipalityId}/assets/{id}", consumes = APPLICATION_JSON_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> updateAsset(
		@PathVariable String municipalityId,
		@PathVariable String id,
		@RequestBody AssetUpdateRequest asset);

	/**
	 * Deletes the post with its attachments. The relation PartyAssets wrote for it stays.
	 */
	@DeleteMapping(path = "/{municipalityId}/assets/{id}", produces = ALL_VALUE)
	ResponseEntity<Void> deleteAsset(
		@PathVariable String municipalityId,
		@PathVariable String id);

	@GetMapping(path = "/{municipalityId}/assets/{id}/attachments", produces = APPLICATION_JSON_VALUE)
	List<AssetAttachment> getAttachments(
		@PathVariable String municipalityId,
		@PathVariable String id);

	@PostMapping(path = "/{municipalityId}/assets/{id}/attachments", consumes = MULTIPART_FORM_DATA_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> createAttachment(
		@PathVariable String municipalityId,
		@PathVariable String id,
		@RequestPart(ATTACHMENT_PART) MultipartFile attachment,
		@RequestPart(name = CATEGORY_PART, required = false) String category);
}
