package se.sundsvall.aotloader.service.mapper;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.partyassets.Status;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.service.model.AssetData;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.support.MigrationException;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.Constants.ASSET_ORIGIN;
import static se.sundsvall.aotloader.Constants.ASSET_PARAMETER_ERRAND_ID;
import static se.sundsvall.aotloader.Constants.ASSET_PARAMETER_LEGAL_BASIS;
import static se.sundsvall.aotloader.Constants.ASSET_TEXT_MAX_LENGTH;
import static se.sundsvall.aotloader.Constants.PARAMETER_PREMISES_MUNICIPALITY_ID;
import static se.sundsvall.aotloader.Constants.RELATION_SERVICE_SUPPORT_MANAGEMENT;
import static se.sundsvall.aotloader.Constants.RELATION_TYPE_LINK;
import static se.sundsvall.aotloader.Constants.RESOURCE_TYPE_CASE;

/**
 * Maps a candidate to its post in PartyAssets (section 7.4).
 */
@Component
public class AssetMapper {

	// A value longer than PartyAssets stores is cut and points to the errand, where the whole text is; not the legal basis
	private static final String TRUNCATED_SUFFIX = " … (se ärendet)";

	private final AotLoaderProperties properties;

	public AssetMapper(final AotLoaderProperties properties) {
		this.properties = properties;
	}

	/**
	 * @return the keys of the parameters of the post that are longer than PartyAssets takes, and which {@link #toAsset}
	 *         therefore cuts, in the order of the parameters
	 */
	public static List<String> truncatedParameters(final AssetData asset) {
		return asset.parameters().entrySet().stream()
			.filter(parameter -> parameter.getValue() != null && parameter.getValue().length() > ASSET_TEXT_MAX_LENGTH)
			.map(Map.Entry::getKey)
			.toList();
	}

	/**
	 * @return the legal basis as long as PartyAssets takes it: the references that fit whole, without pointing to the
	 *         errand, which does not hold the legal basis; a first reference that does not fit is cut
	 */
	static String legalBasisWithin(final String value) {
		if (value == null || value.length() <= ASSET_TEXT_MAX_LENGTH) {
			return value;
		}
		var kept = "";
		for (final var reference : value.split("; ")) {
			final var longer = kept.isEmpty() ? reference : kept + "; " + reference;
			if (longer.length() > ASSET_TEXT_MAX_LENGTH) {
				break;
			}
			kept = longer;
		}
		return kept.isEmpty() ? Texts.truncate(value, ASSET_TEXT_MAX_LENGTH, null) : kept;
	}

	/**
	 * Maps the candidate to a post in PartyAssets. The asset id is the id of the errand (decision 4).
	 *
	 * @param  candidate      the candidate
	 * @param  municipalityId the municipality the run is started for
	 * @param  errandId       the id of the errand
	 * @param  partyId        the partyId of the holder
	 * @param  expired        true to create the post as EXPIRED (the exception list said so)
	 * @return                the request
	 */
	public AssetCreateRequest toAsset(final Candidate candidate, final String municipalityId, final String errandId, final String partyId, final boolean expired) {
		final var asset = Optional.ofNullable(candidate.asset()).orElseThrow(() -> new MigrationException("Ärendetypen har inget tillstånd i PartyAssets"));
		final var settings = properties.errandType(candidate.type());
		final var title = Texts.isBlank(asset.titleSuffix()) ? settings.assetTitle() : Texts.title(settings.assetTitle(), asset.titleSuffix());
		final var parameters = new LinkedHashMap<String, String>();
		parameters.put(ASSET_PARAMETER_ERRAND_ID, errandId);
		asset.parameters().forEach((key, value) -> parameters.put(key, ASSET_PARAMETER_LEGAL_BASIS.equals(key) ? legalBasisWithin(value) : Texts.truncate(value, ASSET_TEXT_MAX_LENGTH, TRUNCATED_SUFFIX)));
		parameters.put(PARAMETER_PREMISES_MUNICIPALITY_ID, properties.premisesMunicipalityOf(candidate.place(), municipalityId));
		return new AssetCreateRequest()
			.assetId(errandId)
			.origin(ASSET_ORIGIN)
			.partyId(partyId)
			.type(settings.assetType())
			.title(Texts.truncate(title, ASSET_TEXT_MAX_LENGTH, null))
			.description(Texts.truncate(asset.description(), ASSET_TEXT_MAX_LENGTH, TRUNCATED_SUFFIX))
			.issued(asset.issued())
			.validTo(asset.validTo())
			.status(expired ? Status.EXPIRED : Status.ACTIVE)
			.additionalParameters(parameters);
	}

	/**
	 * Compares a post with what it is to be, as far as PartyAssets lets the post change: its title, its last day and its
	 * parameters.
	 *
	 * @param  found  the post in PartyAssets
	 * @param  wanted the post the item gives now
	 * @return        the change of what differs, or empty when nothing does
	 */
	public Optional<AssetUpdateRequest> toContentUpdate(final Asset found, final AssetCreateRequest wanted) {
		final var update = new AssetUpdateRequest();
		var changes = false;
		if (!Objects.equals(found.getTitle(), wanted.getTitle())) {
			// A blank title removes the title in PartyAssets, while a missing one leaves it
			update.title(Optional.ofNullable(wanted.getTitle()).orElse(""));
			changes = true;
		}
		if (!Objects.equals(found.getValidTo(), wanted.getValidTo())) {
			if (wanted.getValidTo() == null) {
				update.indefinitely(true);
			} else {
				update.validTo(wanted.getValidTo());
			}
			changes = true;
		}
		if (!Objects.equals(Optional.ofNullable(found.getAdditionalParameters()).orElse(Map.of()), wanted.getAdditionalParameters())) {
			// PartyAssets replaces the whole map
			update.additionalParameters(wanted.getAdditionalParameters());
			changes = true;
		}
		return changes ? Optional.of(update) : Optional.empty();
	}

	/**
	 * @return the relation PartyAssets writes from the post to the errand, in the format pw-alkt sends to /asset-drafts
	 */
	public String toSourceReference(final String errandId) {
		return se.sundsvall.dept44.support.Relation.create(RELATION_TYPE_LINK,
			se.sundsvall.dept44.support.Relation.ResourceIdentifier.create(errandId, RESOURCE_TYPE_CASE, RELATION_SERVICE_SUPPORT_MANAGEMENT, properties.namespace()), null)
			.toRelationString();
	}
}
