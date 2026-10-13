package se.sundsvall.aotloader.service.mapper;

import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.Status;
import generated.se.sundsvall.relation.Relation;
import generated.se.sundsvall.relation.ResourceIdentifier;
import generated.se.sundsvall.supportmanagement.Classification;
import generated.se.sundsvall.supportmanagement.ContactChannel;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Errand.LifecycleEnum;
import generated.se.sundsvall.supportmanagement.ErrandLabel;
import generated.se.sundsvall.supportmanagement.ExternalTag;
import generated.se.sundsvall.supportmanagement.Priority;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.PartyIds;
import se.sundsvall.aotloader.service.model.Person;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.source.ParameterList;
import se.sundsvall.aotloader.support.MigrationException;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.Constants.ASSET_ORIGIN;
import static se.sundsvall.aotloader.Constants.ASSET_PARAMETER_ERRAND_ID;
import static se.sundsvall.aotloader.Constants.ASSET_TEXT_MAX_LENGTH;
import static se.sundsvall.aotloader.Constants.ERRAND_PARAMETER_MAX_LENGTH;
import static se.sundsvall.aotloader.Constants.ERRAND_PARAMETER_PREMISES_EMAIL;
import static se.sundsvall.aotloader.Constants.ERRAND_PARAMETER_PREMISES_NAME;
import static se.sundsvall.aotloader.Constants.ERRAND_PARAMETER_PREMISES_PHONE;
import static se.sundsvall.aotloader.Constants.ERRAND_PARAMETER_PREMISES_POSTAL_AREA;
import static se.sundsvall.aotloader.Constants.ERRAND_PARAMETER_PREMISES_POSTAL_CODE;
import static se.sundsvall.aotloader.Constants.ERRAND_PARAMETER_PREMISES_STREET_ADDRESS;
import static se.sundsvall.aotloader.Constants.LABEL_MIGRATED;
import static se.sundsvall.aotloader.Constants.PARAMETER_CONTACT_PERSON;
import static se.sundsvall.aotloader.Constants.PARAMETER_PBI;
import static se.sundsvall.aotloader.Constants.PARAMETER_PBI_DISPLAY_NAME;
import static se.sundsvall.aotloader.Constants.PARAMETER_POSITION;
import static se.sundsvall.aotloader.Constants.PARAMETER_POSITION_DISPLAY_NAME;
import static se.sundsvall.aotloader.Constants.RELATION_SERVICE_SUPPORT_MANAGEMENT;
import static se.sundsvall.aotloader.Constants.RELATION_TYPE_LINK;
import static se.sundsvall.aotloader.Constants.RESOURCE_TYPE_CASE;
import static se.sundsvall.aotloader.Constants.TAG_MIGRATION_ID;

/**
 * Maps a candidate to what the target systems get: the errand in SupportManagement (section 7.3), the post in
 * PartyAssets (section 7.4) and the relation between an inspection errand and its permit errand (section 7.7.1).
 */
@Component
public class TargetMapper {

	static final String CONTACT_EMAIL = "EMAIL";
	static final String CONTACT_PHONE = "PHONE";
	static final String DASH = "–";
	// A value longer than PartyAssets stores is cut and points to the errand, where the whole text is
	private static final String TRUNCATED_SUFFIX = " … (se ärendet)";

	private final AotLoaderProperties properties;

	public TargetMapper(final AotLoaderProperties properties) {
		this.properties = properties;
	}

	/**
	 * Maps the candidate to a new errand, created as a draft.
	 *
	 * @param  candidate the candidate
	 * @param  parties   the partyIds found for the holder and the persons
	 * @param  labelIds  the ids of the labels by resource path, as {@link LabelPaths#normalize} gives them
	 * @return           the errand
	 */
	public Errand toNewDraft(final Candidate candidate, final PartyIds parties, final Map<String, String> labelIds) {
		return toErrand(candidate, parties, labelIds)
			.lifecycle(LifecycleEnum.DRAFT)
			.reporterUserId(properties.reporterUserId());
	}

	/**
	 * Maps the candidate to the patch that updates a draft with what the source says now. The patch has no reporter and
	 * no life cycle: SupportManagement refuses a patch with a reporter, and the draft stays a draft.
	 *
	 * @param  candidate the candidate
	 * @param  parties   the partyIds found for the holder and the persons
	 * @param  labelIds  the ids of the labels by resource path, as {@link LabelPaths#normalize} gives them
	 * @return           the patch
	 */
	public Errand toDraftUpdate(final Candidate candidate, final PartyIds parties, final Map<String, String> labelIds) {
		return toErrand(candidate, parties, labelIds);
	}

	private Errand toErrand(final Candidate candidate, final PartyIds parties, final Map<String, String> labelIds) {
		final var settings = properties.errandType(candidate.type());
		return new Errand()
			.title(candidate.title())
			.description(candidate.description())
			.priority(Priority.MEDIUM)
			.status(properties.errandStatus())
			.classification(new Classification().category(settings.category()).type(settings.type()))
			.labels(labels(candidate, labelIds))
			.channel(properties.channel())
			.businessRelated(true)
			.externalTags(externalTags(candidate))
			.stakeholders(stakeholders(candidate, parties))
			.parameters(Stream.concat(candidate.parameters().stream(), premisesParameters(candidate.place()).stream()).map(TargetMapper::toParameter).toList());
	}

	/**
	 * @return the patch that makes a draft an active errand
	 */
	public Errand toActivation() {
		return new Errand().lifecycle(LifecycleEnum.ACTIVE);
	}

	/**
	 * Maps the candidate to a post in PartyAssets. The asset id is the id of the errand (decision 4).
	 *
	 * @param  candidate the candidate
	 * @param  errandId  the id of the errand
	 * @param  partyId   the partyId of the holder
	 * @param  expired   true to create the post as EXPIRED (the exception list said so)
	 * @return           the request
	 */
	public AssetCreateRequest toAsset(final Candidate candidate, final String errandId, final String partyId, final boolean expired) {
		final var asset = Optional.ofNullable(candidate.asset()).orElseThrow(() -> new MigrationException("Ärendetypen har inget tillstånd i PartyAssets"));
		final var settings = properties.errandType(candidate.type());
		final var title = Texts.isBlank(asset.titleSuffix()) ? settings.assetTitle() : settings.assetTitle() + " " + DASH + " " + asset.titleSuffix();
		final var parameters = new LinkedHashMap<String, String>();
		parameters.put(ASSET_PARAMETER_ERRAND_ID, errandId);
		asset.parameters().forEach((key, value) -> parameters.put(key, Texts.truncate(value, ASSET_TEXT_MAX_LENGTH, TRUNCATED_SUFFIX)));
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
	 * @return the relation PartyAssets writes from the post to the errand, in the format pw-alkt sends to /asset-drafts
	 */
	public String toSourceReference(final String errandId) {
		return se.sundsvall.dept44.support.Relation.create(RELATION_TYPE_LINK,
			se.sundsvall.dept44.support.Relation.ResourceIdentifier.create(errandId, RESOURCE_TYPE_CASE, RELATION_SERVICE_SUPPORT_MANAGEMENT, properties.namespace()), null)
			.toRelationString();
	}

	/**
	 * @return the relation from the permit errand to the inspection errand (section 7.7.1)
	 */
	public Relation toRelation(final String permitErrandId, final String inspectionErrandId) {
		return new Relation()
			.type(RELATION_TYPE_LINK)
			.source(toResourceIdentifier(permitErrandId))
			.target(toResourceIdentifier(inspectionErrandId));
	}

	/**
	 * @return the role of the holder: the permit holder, or for an inspection errand the holder it concerned
	 */
	public String holderRole(final ErrandType type) {
		return type.getKind() == Kind.TILLSYN ? properties.roles().inspectionHolder() : properties.roles().permitHolder();
	}

	private ResourceIdentifier toResourceIdentifier(final String errandId) {
		return new ResourceIdentifier()
			.resourceId(errandId)
			.type(RESOURCE_TYPE_CASE)
			.service(RELATION_SERVICE_SUPPORT_MANAGEMENT)
			.namespace(properties.namespace());
	}

	private static List<ErrandLabel> labels(final Candidate candidate, final Map<String, String> labelIds) {
		final var paths = new LinkedHashSet<String>();
		paths.add(LABEL_MIGRATED);
		paths.addAll(candidate.labelPaths());
		return paths.stream()
			.map(path -> Optional.ofNullable(labelIds.get(LabelPaths.normalize(path)))
				.orElseThrow(() -> new MigrationException("Etiketten %s finns inte i namespace".formatted(path))))
			// Only the id, never the version: an outdated version is refused with 412
			.map(id -> new ErrandLabel().id(id))
			.toList();
	}

	private static Set<ExternalTag> externalTags(final Candidate candidate) {
		final var tags = new LinkedHashSet<ExternalTag>();
		tags.add(new ExternalTag().key(TAG_MIGRATION_ID).value(candidate.key().migrationId()));
		candidate.externalTags().forEach((key, value) -> tags.add(new ExternalTag().key(key).value(value)));
		return tags;
	}

	private List<Stakeholder> stakeholders(final Candidate candidate, final PartyIds parties) {
		final var stakeholders = new ArrayList<Stakeholder>();
		Optional.ofNullable(candidate.holder()).ifPresent(holder -> {
			final var stakeholder = new Stakeholder()
				.role(holderRole(candidate.type()))
				.organizationName(holder.name())
				.address(holder.address())
				.zipCode(holder.zipCode())
				.city(holder.city())
				.contactChannels(contactChannels(holder.email(), holder.phone()))
				.parameters(toParameters(contactPersonParameter(holder.contactPerson())));
			parties.holderPartyId().ifPresent(partyId -> stakeholder.externalId(partyId).externalIdType(holder.legalId().type().name()));
			stakeholders.add(stakeholder);
		});
		for (var i = 0; i < candidate.persons().size(); i++) {
			final var person = candidate.persons().get(i);
			final var stakeholder = new Stakeholder()
				.role(properties.roles().contact())
				.firstName(person.name())
				.parameters(toParameters(personParameters(person)));
			parties.personPartyId(i).ifPresent(partyId -> stakeholder.externalId(partyId).externalIdType(person.legalId().type().name()));
			stakeholders.add(stakeholder);
		}
		return stakeholders;
	}

	/**
	 * The parameter PBI = true, which tells a person with significant influence apart from other contacts, and the
	 * positions when there are any.
	 */
	private static List<Parameter> personParameters(final Person person) {
		final var parameters = new ArrayList<Parameter>();
		parameters.add(new Parameter(PARAMETER_PBI, PARAMETER_PBI_DISPLAY_NAME, List.of(Boolean.TRUE.toString())));
		if (!person.positions().isEmpty()) {
			parameters.add(new Parameter(PARAMETER_POSITION, PARAMETER_POSITION_DISPLAY_NAME, person.positions()));
		}
		return parameters;
	}

	/**
	 * The serving place or sales place as parameters of the errand, under the keys pw-alkt reads (section 7.2.1), followed
	 * by the place's own parameters, e.g. the restaurant number.
	 */
	private static List<Parameter> premisesParameters(final Place place) {
		if (place == null) {
			return List.of();
		}
		final var premises = new ParameterList()
			.add(ERRAND_PARAMETER_PREMISES_NAME, "Ställets namn", place.name())
			.add(ERRAND_PARAMETER_PREMISES_STREET_ADDRESS, "Ställets gatuadress", place.address())
			.add(ERRAND_PARAMETER_PREMISES_POSTAL_CODE, "Ställets postnummer", place.zipCode())
			.add(ERRAND_PARAMETER_PREMISES_POSTAL_AREA, "Ställets postort", place.city())
			.add(ERRAND_PARAMETER_PREMISES_PHONE, "Ställets telefon", place.phone())
			.add(ERRAND_PARAMETER_PREMISES_EMAIL, "Ställets e-post", place.email())
			.build();
		return Stream.concat(premises.stream(), place.parameters().stream()).toList();
	}

	private static List<Parameter> contactPersonParameter(final String contactPerson) {
		return Texts.isBlank(contactPerson) ? List.of() : List.of(new Parameter(PARAMETER_CONTACT_PERSON, "Kontaktperson", List.of(contactPerson)));
	}

	private static List<ContactChannel> contactChannels(final String email, final String phone) {
		final var channels = new ArrayList<ContactChannel>();
		Optional.ofNullable(Texts.trimToNull(email)).ifPresent(value -> channels.add(new ContactChannel().type(CONTACT_EMAIL).value(value)));
		Optional.ofNullable(Texts.trimToNull(phone)).ifPresent(value -> channels.add(new ContactChannel().type(CONTACT_PHONE).value(value)));
		return channels;
	}

	private static List<generated.se.sundsvall.supportmanagement.Parameter> toParameters(final List<Parameter> parameters) {
		return parameters.stream().map(TargetMapper::toParameter).toList();
	}

	/**
	 * A value longer than SupportManagement takes is split into several values (section 7.3.3).
	 */
	static generated.se.sundsvall.supportmanagement.Parameter toParameter(final Parameter parameter) {
		return new generated.se.sundsvall.supportmanagement.Parameter()
			.key(parameter.key())
			.displayName(parameter.displayName())
			.values(parameter.values().stream().flatMap(value -> Texts.split(value, ERRAND_PARAMETER_MAX_LENGTH).stream()).toList());
	}

}
