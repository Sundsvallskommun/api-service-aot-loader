package se.sundsvall.aotloader.integration.partyassets;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetAttachment;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.integration.FileRefusal;
import se.sundsvall.aotloader.support.ByteArrayMultipartFile;
import se.sundsvall.aotloader.support.Locations;
import se.sundsvall.aotloader.support.MigrationException;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static java.util.Collections.emptyList;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
public class PartyAssetsIntegration {

	private final PartyAssetsClient client;

	PartyAssetsIntegration(final PartyAssetsClient client) {
		this.client = client;
	}

	/**
	 * @return the post, or empty when there is none with the id
	 */
	public Optional<Asset> getAsset(final String municipalityId, final String id) {
		try {
			return Optional.ofNullable(client.getAsset(municipalityId, id));
		} catch (final ThrowableProblem problem) {
			if (problem.getStatus() == NOT_FOUND) {
				return Optional.empty();
			}
			throw problem;
		}
	}

	/**
	 * @return                    the post with the asset id, or empty when the party has none
	 * @throws MigrationException when the party has more than one
	 */
	public Optional<Asset> findAsset(final String municipalityId, final String partyId, final String assetId) {
		final var assets = Optional.ofNullable(client.getAssets(municipalityId, partyId, assetId)).orElse(emptyList()).stream()
			.filter(asset -> assetId.equals(asset.getAssetId()))
			.toList();
		if (assets.size() > 1) {
			throw new MigrationException("PartyAssets har %d poster med samma tillstånds-id".formatted(assets.size()));
		}
		return assets.stream().findFirst();
	}

	/**
	 * @return the internal id of the created post, or empty when PartyAssets answers that it already exists
	 */
	public Optional<String> createAsset(final String municipalityId, final String sourceReference, final AssetCreateRequest request) {
		try {
			return Optional.of(Locations.idOf(client.createAsset(municipalityId, sourceReference, request), "tillståndet"));
		} catch (final ThrowableProblem problem) {
			if (problem.getStatus() == CONFLICT) {
				return Optional.empty();
			}
			throw problem;
		}
	}

	/**
	 * Changes the post. What the request leaves out stays as it is, and PartyAssets refuses a change of more than the
	 * status
	 * and its reason unless the post is ACTIVE or TEMPORARY.
	 */
	public void updateAsset(final String municipalityId, final String id, final AssetUpdateRequest request) {
		client.updateAsset(municipalityId, id, request);
	}

	/**
	 * @return the attachments of the post
	 */
	public List<AssetAttachment> getAttachments(final String municipalityId, final String id) {
		return Optional.ofNullable(client.getAttachments(municipalityId, id)).orElse(emptyList());
	}

	/**
	 * Uploads a file to the post, which gives the post a new revision. PartyAssets takes no file on an EXPIRED post.
	 *
	 * @param  fileName the name the file gets on the post
	 * @param  content  the content of the file
	 * @param  category what the file depicts
	 * @return          true when PartyAssets took the file, false when it refused the file itself
	 */
	public boolean createAttachment(final String municipalityId, final String id, final String fileName, final byte[] content, final String category) {
		try {
			client.createAttachment(municipalityId, id, new ByteArrayMultipartFile(PartyAssetsClient.ATTACHMENT_PART, fileName, content), category);
			return true;
		} catch (final ThrowableProblem problem) {
			if (FileRefusal.isRefusal(problem)) {
				return false;
			}
			throw problem;
		}
	}
}
