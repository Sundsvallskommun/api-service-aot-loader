package se.sundsvall.aotloader.service.mapper;

import generated.se.sundsvall.supportmanagement.ContactChannel;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.PartyIds;
import se.sundsvall.aotloader.service.model.PartyType;
import se.sundsvall.aotloader.service.model.Person;
import se.sundsvall.aotloader.support.LegalIds;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.Constants.PARAMETER_ORGANIZATION_NUMBER;
import static se.sundsvall.aotloader.Constants.PARAMETER_ORGANIZATION_NUMBER_DISPLAY_NAME;
import static se.sundsvall.aotloader.Constants.PARAMETER_PBI;
import static se.sundsvall.aotloader.Constants.PARAMETER_PBI_ROLE;
import static se.sundsvall.aotloader.Constants.PARAMETER_PBI_SOURCE;
import static se.sundsvall.aotloader.Constants.PBI_SOURCE_MANUAL;
import static se.sundsvall.aotloader.service.mapper.ErrandParameterMapper.toParameters;

/**
 * Maps the holder, its contact person and the persons with significant influence of a candidate to the stakeholders of
 * its errand (section 7.2).
 */
final class StakeholderMapper {

	static final String CONTACT_EMAIL = "EMAIL";
	static final String CONTACT_PHONE = "PHONE";
	// Draken writes it on every stakeholder it saves
	static final String COUNTRY = "SVERIGE";

	private final AotLoaderProperties properties;

	StakeholderMapper(final AotLoaderProperties properties) {
		this.properties = properties;
	}

	/**
	 * @param  parties the partyIds found for the holder and the persons
	 * @return         the stakeholders of the errand: the holder and its contact person, and the persons with significant
	 *                 influence that are not the holder
	 */
	List<Stakeholder> stakeholders(final Candidate candidate, final PartyIds parties) {
		final var stakeholders = new ArrayList<Stakeholder>();
		final var holder = candidate.holder();
		if (holder != null) {
			stakeholders.add(holderStakeholder(candidate, holder, parties));
			// The holder's contact person has only a name; it lacks the parameter PBI that marks a person with significant
			// influence
			Optional.ofNullable(Texts.trimToNull(holder.contactPerson())).ifPresent(name -> stakeholders.add(new Stakeholder()
				.role(properties.roles().contact())
				.externalIdType(PartyType.PRIVATE.getExternalIdType())
				.firstName(name)
				.country(COUNTRY)));
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
	 * @return the role of the holder: the permit holder, or for an inspection errand, visit or documentation errand the
	 *         holder
	 *         it concerned
	 */
	String holderRole(final ErrandType type) {
		return type.getKind().hasConcernedHolder() ? properties.roles().inspectionHolder() : properties.roles().permitHolder();
	}

	/**
	 * The holder, with its partyId when Party knows it, and otherwise with the type of its number as a stakeholder added by
	 * hand has it (see {@link #externalIdTypeOf}). A holder that does not become a company has its name also as first
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
			.country(COUNTRY)
			.contactChannels(contactChannels(holder.email(), holder.phone()));
		parties.holderPartyId().ifPresentOrElse(partyId -> identify(stakeholder, partyId, holder.legalId(), List.of()),
			() -> stakeholder.externalIdType(externalIdTypeOf(holder.legalId())));
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
			.country(COUNTRY)
			.parameters(toParameters(parameters));
		if (person.isCompany()) {
			stakeholder.organizationName(person.name());
		}
		if (partyId == null) {
			stakeholder.externalIdType(externalIdTypeOf(person.legalId()));
		} else {
			identify(stakeholder, partyId, person.legalId(), parameters);
		}
		return stakeholder;
	}

	/**
	 * @return the type a stakeholder without partyId gets, the one Draken and Katla give a stakeholder added by hand: a
	 *         company by its organization number, and a person otherwise, also when the number is missing
	 */
	private static String externalIdTypeOf(final LegalId legalId) {
		return Optional.ofNullable(legalId).map(LegalId::type).orElse(PartyType.PRIVATE).getExternalIdType();
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

	private static List<ContactChannel> contactChannels(final String email, final String phone) {
		final var channels = new ArrayList<ContactChannel>();
		Optional.ofNullable(Texts.trimToNull(email)).ifPresent(value -> channels.add(new ContactChannel().type(CONTACT_EMAIL).value(value)));
		Optional.ofNullable(Texts.trimToNull(phone)).ifPresent(value -> channels.add(new ContactChannel().type(CONTACT_PHONE).value(value)));
		return channels;
	}
}
