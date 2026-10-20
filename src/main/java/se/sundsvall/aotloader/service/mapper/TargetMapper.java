package se.sundsvall.aotloader.service.mapper;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.partyassets.Status;
import generated.se.sundsvall.relation.Relation;
import generated.se.sundsvall.relation.ResourceIdentifier;
import generated.se.sundsvall.supportmanagement.Classification;
import generated.se.sundsvall.supportmanagement.ContactChannel;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Errand.LifecycleEnum;
import generated.se.sundsvall.supportmanagement.ErrandLabel;
import generated.se.sundsvall.supportmanagement.ExternalTag;
import generated.se.sundsvall.supportmanagement.Investigation;
import generated.se.sundsvall.supportmanagement.Measure;
import generated.se.sundsvall.supportmanagement.Priority;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.service.model.AssetData;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.InvestigationData;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.PartyIds;
import se.sundsvall.aotloader.service.model.PartyType;
import se.sundsvall.aotloader.service.model.Person;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.source.ParameterList;
import se.sundsvall.aotloader.support.LegalIds;
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
import static se.sundsvall.aotloader.Constants.PARAMETER_ORGANIZATION_NUMBER;
import static se.sundsvall.aotloader.Constants.PARAMETER_ORGANIZATION_NUMBER_DISPLAY_NAME;
import static se.sundsvall.aotloader.Constants.PARAMETER_PBI;
import static se.sundsvall.aotloader.Constants.PARAMETER_PBI_ROLE;
import static se.sundsvall.aotloader.Constants.PARAMETER_PBI_SOURCE;
import static se.sundsvall.aotloader.Constants.PARAMETER_PREMISES_MUNICIPALITY_ID;
import static se.sundsvall.aotloader.Constants.PBI_SOURCE_MANUAL;
import static se.sundsvall.aotloader.Constants.RELATION_SERVICE_SUPPORT_MANAGEMENT;
import static se.sundsvall.aotloader.Constants.RELATION_TYPE_LINK;
import static se.sundsvall.aotloader.Constants.RESOURCE_TYPE_CASE;
import static se.sundsvall.aotloader.Constants.TAG_MIGRATION_ID;
import static se.sundsvall.aotloader.configuration.ClockConfiguration.SWEDISH_TIME;

/**
 * Maps a candidate to what the target systems get: the errand in SupportManagement (section 7.3), the post in
 * PartyAssets (section 7.4) and the relation between an inspection errand and its permit errand (section 7.7.1).
 */
@Component
public class TargetMapper {

	static final String CONTACT_EMAIL = "EMAIL";
	static final String CONTACT_PHONE = "PHONE";
	// A value longer than PartyAssets stores is cut and points to the errand, where the whole text is
	private static final String TRUNCATED_SUFFIX = " … (se ärendet)";
	private static final String INVESTIGATION_ACTIVE = "ACTIVE";
	private static final String INVESTIGATION_COMPLETED = "COMPLETED";
	private static final int INVESTIGATION_TITLE_MAX_LENGTH = 255;
	private static final String MEASURE_COMPLETED = "COMPLETED";
	private static final int MEASURE_TITLE_MAX_LENGTH = 255;
	private static final int MEASURE_DESCRIPTION_MAX_LENGTH = 3000;

	private final AotLoaderProperties properties;
	private final Clock clock;

	public TargetMapper(final AotLoaderProperties properties, final Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Maps the candidate to a new errand, created as a draft.
	 *
	 * @param  candidate      the candidate
	 * @param  municipalityId the municipality the run is started for
	 * @param  parties        the partyIds found for the holder and the persons
	 * @param  labelIds       the ids of the labels by resource path, as {@link LabelPaths#normalize} gives them
	 * @return                the errand
	 */
	public Errand toNewDraft(final Candidate candidate, final String municipalityId, final PartyIds parties, final Map<String, String> labelIds) {
		return toErrand(candidate, municipalityId, parties, labelIds)
			.lifecycle(LifecycleEnum.DRAFT)
			.reporterUserId(properties.reporterUserId());
	}

	/**
	 * Maps the candidate to the patch that updates a draft with what the source says now. The patch has no reporter and
	 * no life cycle: SupportManagement refuses a patch with a reporter, and the draft stays a draft.
	 *
	 * @param  candidate      the candidate
	 * @param  municipalityId the municipality the run is started for
	 * @param  parties        the partyIds found for the holder and the persons
	 * @param  labelIds       the ids of the labels by resource path, as {@link LabelPaths#normalize} gives them
	 * @return                the patch
	 */
	public Errand toDraftUpdate(final Candidate candidate, final String municipalityId, final PartyIds parties, final Map<String, String> labelIds) {
		return toErrand(candidate, municipalityId, parties, labelIds);
	}

	private Errand toErrand(final Candidate candidate, final String municipalityId, final PartyIds parties, final Map<String, String> labelIds) {
		final var settings = properties.errandType(candidate.type());
		final var errand = new Errand()
			.title(candidate.title())
			.description(Texts.paragraphs(candidate.description()))
			.priority(Priority.MEDIUM)
			.status(properties.errandStatus())
			.classification(new Classification().category(settings.category()).type(settings.type()))
			.labels(labels(candidate, labelIds))
			.channel(properties.channel())
			.businessRelated(true)
			.externalTags(externalTags(candidate))
			.stakeholders(stakeholders(candidate, parties))
			.parameters(Stream.of(candidate.parameters(), premisesParameters(candidate.place()), List.of(premisesMunicipalityParameter(candidate, municipalityId)))
				.flatMap(List::stream)
				.map(TargetMapper::toParameter)
				.toList());
		// A visit always sends its list, also an empty one, so that an update of a draft replaces what an earlier run gave it
		if (candidate.type().getKind() == Kind.TILLSYNSBESOK) {
			errand.measures(measures(candidate, settings.measureType()));
		}
		return errand;
	}

	/**
	 * @param  type the measure type of the measures, that of the settings of the errand type
	 * @return      the measures of an inspection visit, its remarks (decision 57): added by the migration in the role
	 *              Draken gives every measure, and completed when the visit was, so that no remark of a closed visit
	 *              counts as an open deficiency
	 */
	private List<Measure> measures(final Candidate candidate, final String type) {
		return candidate.measures().stream()
			.map(measure -> new Measure()
				.type(type)
				.title(Texts.truncate(measure.title(), MEASURE_TITLE_MAX_LENGTH, null))
				.description(Texts.truncate(measure.description(), MEASURE_DESCRIPTION_MAX_LENGTH, null))
				.addedByUser(properties.reporterUserId())
				.addedByRole(properties.roles().measureAddedBy())
				.status(MEASURE_COMPLETED)
				.completedAt(untilNow(measure.completedAt())))
			.toList();
	}

	/**
	 * @return the patch that makes a draft an active errand
	 */
	public Errand toActivation() {
		return new Errand().lifecycle(LifecycleEnum.ACTIVE);
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
		asset.parameters().forEach((key, value) -> parameters.put(key, Texts.truncate(value, ASSET_TEXT_MAX_LENGTH, TRUNCATED_SUFFIX)));
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
	 * @return the investigation of an inspection errand, visit or permit, completed when the source tells when the
	 *         inspection or trial was, with the times in Swedish time (decisions 41 and 46) and the texts of the source
	 *         (decisions 44–46); an end later than now, which the source has in error or for a planned inspection, leaves
	 *         it active without an end
	 */
	public Investigation toInvestigation(final InvestigationData investigation) {
		final var completedAt = untilNow(investigation.completedAt());
		final var texts = investigation.texts();
		return new Investigation()
			.status(completedAt == null ? INVESTIGATION_ACTIVE : INVESTIGATION_COMPLETED)
			.title(Texts.truncate(investigation.title(), INVESTIGATION_TITLE_MAX_LENGTH, null))
			.description(texts.description())
			.summary(texts.summary())
			.conclusion(texts.conclusion())
			.startedAt(inSwedishTime(investigation.startedAt()))
			.completedAt(completedAt);
	}

	private static OffsetDateTime inSwedishTime(final LocalDateTime time) {
		return time == null ? null : time.atZone(SWEDISH_TIME).toOffsetDateTime();
	}

	/**
	 * @return the time in Swedish time; null when it is later than now, which the source has in error or for a planned
	 *         inspection
	 */
	private OffsetDateTime untilNow(final LocalDateTime time) {
		return Optional.ofNullable(inSwedishTime(time))
			.filter(at -> !at.toInstant().isAfter(clock.instant()))
			.orElse(null);
	}

	/**
	 * @return the role of the holder: the permit holder, or for an inspection errand or visit the holder it concerned
	 */
	public String holderRole(final ErrandType type) {
		return type.getKind().isInspection() ? properties.roles().inspectionHolder() : properties.roles().permitHolder();
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
		final var holder = candidate.holder();
		if (holder != null) {
			stakeholders.add(holderStakeholder(candidate, holder, parties));
			// The holder's contact person has only a name; it lacks the parameter PBI that marks a person with significant
			// influence
			Optional.ofNullable(Texts.trimToNull(holder.contactPerson())).ifPresent(name -> stakeholders.add(new Stakeholder()
				.role(properties.roles().contact())
				.firstName(name)));
		}
		for (var i = 0; i < candidate.persons().size(); i++) {
			final var person = candidate.persons().get(i);
			if (!isHolder(holder, person)) {
				stakeholders.add(personStakeholder(person, parties.personPartyId(i).orElse(null)));
			}
		}
		return stakeholders;
	}

	/**
	 * The holder, with its partyId when Party knows it. A holder that does not become a company has its name also as first
	 * name, since Draken names such a stakeholder by its first and last name. A sole trader who is also among the persons
	 * with significant influence is marked as one here, the way Draken marks a stakeholder that is already on the errand:
	 * without a source.
	 */
	private Stakeholder holderStakeholder(final Candidate candidate, final Holder holder, final PartyIds parties) {
		final var stakeholder = new Stakeholder()
			.role(holderRole(candidate.type()))
			.organizationName(holder.name())
			.address(holder.address())
			.zipCode(holder.zipCode())
			.city(holder.city())
			.contactChannels(contactChannels(holder.email(), holder.phone()));
		parties.holderPartyId().ifPresent(partyId -> identify(stakeholder, partyId, holder.legalId(), List.of()));
		if (!PartyType.ENTERPRISE.getExternalIdType().equals(stakeholder.getExternalIdType())) {
			stakeholder.firstName(holder.name());
		}
		final var asPerson = candidate.persons().stream().filter(person -> isHolder(holder, person)).toList();
		if (!asPerson.isEmpty()) {
			final var parameters = new ArrayList<>(Optional.ofNullable(stakeholder.getParameters()).orElse(List.of()));
			parameters.addAll(toParameters(pbiParameters(asPerson.stream().flatMap(person -> person.positions().stream()).toList(), null)));
			stakeholder.parameters(parameters);
		}
		return stakeholder;
	}

	private static boolean isHolder(final Holder holder, final Person person) {
		return holder != null && holder.legalId() != null && holder.legalId().equals(person.legalId());
	}

	/**
	 * A person with significant influence, which can also be a company. The name is the first name, which Draken shows for
	 * such a person, and a company has it also as organization name.
	 *
	 * @param partyId the partyId of the person, null when it has none
	 */
	private Stakeholder personStakeholder(final Person person, final String partyId) {
		final var parameters = pbiParameters(person.positions(), PBI_SOURCE_MANUAL);
		final var stakeholder = new Stakeholder()
			.role(properties.roles().contact())
			.firstName(person.name())
			.parameters(toParameters(parameters));
		if (person.isCompany()) {
			stakeholder.organizationName(person.name());
		}
		if (partyId != null) {
			identify(stakeholder, partyId, person.legalId(), parameters);
		}
		return stakeholder;
	}

	/**
	 * Gives a stakeholder its partyId. A company also gets its organization number in clear text, as the parameter
	 * organizationNumber after the given parameters, the way new errands in AOT have it.
	 */
	private static void identify(final Stakeholder stakeholder, final String partyId, final LegalId legalId, final List<Parameter> parameters) {
		stakeholder.externalId(partyId).externalIdType(legalId.type().getExternalIdType());
		if (legalId.type() == PartyType.ENTERPRISE) {
			final var withNumber = new ArrayList<>(parameters);
			withNumber.add(new Parameter(PARAMETER_ORGANIZATION_NUMBER, PARAMETER_ORGANIZATION_NUMBER_DISPLAY_NAME, List.of(LegalIds.format(legalId))));
			stakeholder.parameters(toParameters(withNumber));
		}
	}

	/**
	 * The parameters of a person with significant influence under the keys Draken writes, without display names: PBI =
	 * true, which tells the person apart from other contacts, the source when there is one, and the positions as the role
	 * in one value when there are any.
	 *
	 * @param source MANUAL for a stakeholder that is on the errand only as a person with significant influence, since a
	 *               case handler entered it in the source; null for one that is on the errand anyway
	 */
	private static List<Parameter> pbiParameters(final List<String> positions, final String source) {
		final var parameters = new ArrayList<Parameter>();
		parameters.add(new Parameter(PARAMETER_PBI, null, List.of(Boolean.TRUE.toString())));
		if (source != null) {
			parameters.add(new Parameter(PARAMETER_PBI_SOURCE, null, List.of(source)));
		}
		if (!positions.isEmpty()) {
			parameters.add(new Parameter(PARAMETER_PBI_ROLE, null, List.of(String.join(", ", positions.stream().distinct().toList()))));
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

	/**
	 * The municipality code of the place, on every errand whether it has a place or not (decision 19).
	 */
	private Parameter premisesMunicipalityParameter(final Candidate candidate, final String municipalityId) {
		return new Parameter(PARAMETER_PREMISES_MUNICIPALITY_ID, "Kommun", List.of(properties.premisesMunicipalityOf(candidate.place(), municipalityId)));
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
