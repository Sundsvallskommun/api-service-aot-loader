package se.sundsvall.aotloader.integration.partyassets;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;
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
}
