package se.sundsvall.aotloader.integration.partyassets;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.support.Locations;
import se.sundsvall.aotloader.support.MigrationException;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static java.util.Collections.emptyList;
import static org.springframework.http.HttpStatus.CONFLICT;

@Component
public class PartyAssetsIntegration {

	private final PartyAssetsClient client;

	PartyAssetsIntegration(final PartyAssetsClient client) {
		this.client = client;
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
}
