package se.sundsvall.aotloader.service.mapper;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetJsonParameter;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.partyassets.Status;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.service.model.AssetSchema;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.JsonContent;
import se.sundsvall.aotloader.support.JsonValues;
import se.sundsvall.aotloader.support.MigrationException;
import se.sundsvall.aotloader.support.Texts;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import static se.sundsvall.aotloader.Constants.ASSET_ORIGIN;
import static se.sundsvall.aotloader.Constants.ASSET_TEXT_MAX_LENGTH;
import static se.sundsvall.aotloader.Constants.RELATION_SERVICE_SUPPORT_MANAGEMENT;
import static se.sundsvall.aotloader.Constants.RELATION_TYPE_LINK;
import static se.sundsvall.aotloader.Constants.RESOURCE_TYPE_CASE;
import static se.sundsvall.aotloader.source.AssetContents.FIELD_CASE_REGISTRATION_NUMBER;
import static se.sundsvall.aotloader.source.AssetContents.FIELD_DECISION;
import static se.sundsvall.aotloader.source.AssetContents.FIELD_DECISION_TEXT;
import static se.sundsvall.aotloader.source.AssetContents.FIELD_ERRAND_ID;
import static se.sundsvall.aotloader.source.AssetContents.FIELD_EXTERNAL_CASE_REGISTRATION_NUMBER;
import static se.sundsvall.aotloader.source.AssetContents.FIELD_LEGAL_BASIS;
import static se.sundsvall.aotloader.source.AssetContents.FIELD_MUNICIPALITY_ID;

/**
 * Maps a candidate to its post in PartyAssets (section 7.4): its fields, and the JSON parameter that holds its content
 * by the AoT schema of its type (decision 87); and what the decision of a permit says of the permit (decision 92).
 */
@Component
public class AssetMapper {

	// A description longer than PartyAssets stores is cut and points to the errand, where the whole text is
	private static final String TRUNCATED_SUFFIX = " … (se ärendet)";

	private final AotLoaderProperties properties;

	public AssetMapper(final AotLoaderProperties properties) {
		this.properties = properties;
	}

	/**
	 * Maps the candidate to a post in PartyAssets. The asset id is the id of the errand (decision 4). The post has its
	 * JSON parameter and no additional parameters, as the AoT solution keeps the permit in the JSON parameter (decision
	 * 87).
	 *
	 * @param  candidate      the candidate
	 * @param  municipalityId the municipality the run is started for
	 * @param  errandId       the id of the errand
	 * @param  partyId        the partyId of the holder
	 * @param  expired        true to create the post as EXPIRED (the exception list said so)
	 * @param  decisionText   the display name of the outcome of the decision of the permit in the namespace, null when the
	 *                        permit has no decision or the name is unknown
	 * @return                the request
	 */
	public AssetCreateRequest toAsset(final Candidate candidate, final String municipalityId, final String errandId, final String partyId, final boolean expired,
		final String decisionText) {
		final var asset = Optional.ofNullable(candidate.asset()).orElseThrow(() -> new MigrationException("Ärendetypen har inget tillstånd i PartyAssets"));
		final var settings = properties.errandType(candidate.type());
		final var schema = properties.jsonParameter(asset.schema().asset());
		final var title = Texts.isBlank(asset.titleSuffix()) ? settings.assetTitle() : Texts.title(settings.assetTitle(), asset.titleSuffix());
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
			.jsonParameters(List.of(new AssetJsonParameter()
				.key(schema.key())
				.schemaId(schema.schemaId())
				.value(jsonValue(asset.content(), asset.schema(), asset.legalBasis(), errandId, properties.premisesMunicipalityOf(candidate.place(), municipalityId), decisionText))));
	}

	/**
	 * Maps what the decision of a permit says of the permit to its JSON parameter by the decision schema of the area
	 * (decisions 92 and 97), written as the JSON parameter of the post is, since Draken reads it as what the permit says.
	 * The decision schema has no diary number of the errand that granted the permit, and its external diary number is the
	 * one the decision was registered under, which Draken shows as the diary number of the decision: the number of the
	 * errand the decision was made in, or else the one the post has.
	 *
	 * @param  candidate      the permit, with its decision and its post, and the legal basis read from its documents
	 * @param  municipalityId the municipality the run is started for
	 * @param  errandId       the id of the errand
	 * @param  decisionText   the display name of the outcome of the decision in the namespace, null when unknown
	 * @return                the JSON parameter, empty when the permit has no decision or no post, or the decision says
	 *                        nothing of the permit
	 */
	public Optional<JsonContent> toDecisionContent(final Candidate candidate, final String municipalityId, final String errandId, final String decisionText) {
		final var asset = candidate.asset();
		final var decision = candidate.decision();
		if (asset == null || decision == null || !decision.hasContent()) {
			return Optional.empty();
		}
		final var value = jsonValue(decision.content(), asset.schema(), asset.legalBasis(), errandId, properties.premisesMunicipalityOf(candidate.place(), municipalityId), decisionText);
		value.remove(FIELD_CASE_REGISTRATION_NUMBER);
		final var registeredUnder = value.path(FIELD_DECISION).path(FIELD_CASE_REGISTRATION_NUMBER);
		if (registeredUnder.isString()) {
			value.put(FIELD_EXTERNAL_CASE_REGISTRATION_NUMBER, registeredUnder.asString());
		}
		return Optional.of(new JsonContent(asset.schema().decision(), value));
	}

	/**
	 * The JSON parameter as it is written: what the source gives, preceded by the id of the errand, with the municipality
	 * in the field of the place, also of a post without a place, as every errand has it (decision 19), and with the text
	 * of the outcome and the legal basis in the decision. A legal basis of a post without a decision gets a decision of its
	 * own. Neither is cut, since the parameter has no limit of length.
	 *
	 * @param content    what the source gives, by the schema
	 * @param schema     the schema
	 * @param legalBasis the legal basis read from the documents of the permit, null when none is read
	 */
	static ObjectNode jsonValue(final ObjectNode content, final AssetSchema schema, final String legalBasis, final String errandId, final String municipalityId,
		final String decisionText) {
		final var value = JsonNodeFactory.instance.objectNode().put(FIELD_ERRAND_ID, errandId);
		// A copy, since the place gets the municipality in place
		value.setAll(content.deepCopy());
		placeOf(value, schema.placeField()).put(FIELD_MUNICIPALITY_ID, municipalityId);
		final var basis = Texts.trimToNull(legalBasis);
		final var text = Texts.trimToNull(decisionText);
		if (value.get(FIELD_DECISION) instanceof final ObjectNode decision) {
			Optional.ofNullable(text).ifPresent(outcome -> decision.put(FIELD_DECISION_TEXT, outcome));
			Optional.ofNullable(basis).ifPresent(found -> decision.put(FIELD_LEGAL_BASIS, found));
		} else if (basis != null) {
			value.putObject(FIELD_DECISION).put(FIELD_LEGAL_BASIS, basis);
		}
		return value;
	}

	private static ObjectNode placeOf(final ObjectNode value, final String field) {
		return value.get(field) instanceof final ObjectNode place ? place : value.putObject(field);
	}

	/**
	 * Compares a post with what it is to be, as far as PartyAssets lets the post change: its title, its last day and its
	 * parameters. Any additional parameters of the post are removed, since its content is in the JSON parameter.
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
		if (!sameJsonParameters(found.getJsonParameters(), wanted.getJsonParameters())) {
			// PartyAssets replaces the whole list
			update.jsonParameters(wanted.getJsonParameters());
			changes = true;
		}
		if (!Optional.ofNullable(found.getAdditionalParameters()).orElse(Map.of()).isEmpty()) {
			// An empty map removes them all, while a missing one leaves them
			update.additionalParameters(Map.of());
			changes = true;
		}
		return changes ? Optional.of(update) : Optional.empty();
	}

	/**
	 * @return true when both lists have the same parameters, by key, schema and value, whatever their order and whatever
	 *         type of number a value has
	 */
	static boolean sameJsonParameters(final List<AssetJsonParameter> found, final List<AssetJsonParameter> wanted) {
		final var one = Optional.ofNullable(found).orElse(List.of());
		final var other = Optional.ofNullable(wanted).orElse(List.of());
		return one.size() == other.size() && one.stream().allMatch(parameter -> other.stream().anyMatch(candidate -> sameJsonParameter(parameter, candidate)));
	}

	private static boolean sameJsonParameter(final AssetJsonParameter one, final AssetJsonParameter other) {
		return Objects.equals(one.getKey(), other.getKey())
			&& Objects.equals(one.getSchemaId(), other.getSchemaId())
			&& JsonValues.same(one.getValue(), other.getValue());
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
