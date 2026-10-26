package se.sundsvall.aotloader.service.mapper;

import generated.se.sundsvall.relation.Relation;
import generated.se.sundsvall.relation.ResourceIdentifier;
import generated.se.sundsvall.supportmanagement.Classification;
import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.DecisionTerm;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Errand.LifecycleEnum;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandLabel;
import generated.se.sundsvall.supportmanagement.ExternalTag;
import generated.se.sundsvall.supportmanagement.Investigation;
import generated.se.sundsvall.supportmanagement.Measure;
import generated.se.sundsvall.supportmanagement.Priority;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.service.model.AssetData;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.DecisionData;
import se.sundsvall.aotloader.service.model.InvestigationData;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.PartyIds;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.source.ParameterList;
import se.sundsvall.aotloader.support.MigrationException;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.Constants.ASSET_PARAMETER_CONDITIONS;
import static se.sundsvall.aotloader.Constants.ASSET_PARAMETER_LEGAL_BASIS;
import static se.sundsvall.aotloader.Constants.DECISION_OUTCOME_APPROVAL;
import static se.sundsvall.aotloader.Constants.DECISION_OUTCOME_APPROVAL_WITH_CONDITIONS;
import static se.sundsvall.aotloader.Constants.DECISION_PARAMETER_DECISION_DATE;
import static se.sundsvall.aotloader.Constants.DECISION_PARAMETER_TEXT;
import static se.sundsvall.aotloader.Constants.DECISION_PARAMETER_VALID_FROM;
import static se.sundsvall.aotloader.Constants.ERRAND_PARAMETER_MAX_LENGTH;
import static se.sundsvall.aotloader.Constants.ERRAND_PARAMETER_PREMISES_EMAIL;
import static se.sundsvall.aotloader.Constants.ERRAND_PARAMETER_PREMISES_NAME;
import static se.sundsvall.aotloader.Constants.ERRAND_PARAMETER_PREMISES_PHONE;
import static se.sundsvall.aotloader.Constants.ERRAND_PARAMETER_PREMISES_POSTAL_AREA;
import static se.sundsvall.aotloader.Constants.ERRAND_PARAMETER_PREMISES_POSTAL_CODE;
import static se.sundsvall.aotloader.Constants.ERRAND_PARAMETER_PREMISES_STREET_ADDRESS;
import static se.sundsvall.aotloader.Constants.LABEL_MIGRATED;
import static se.sundsvall.aotloader.Constants.PARAMETER_PREMISES_MUNICIPALITY_ID;
import static se.sundsvall.aotloader.Constants.RELATION_SERVICE_SUPPORT_MANAGEMENT;
import static se.sundsvall.aotloader.Constants.RELATION_TYPE_LINK;
import static se.sundsvall.aotloader.Constants.RESOURCE_TYPE_CASE;
import static se.sundsvall.aotloader.Constants.TAG_MIGRATION_ID;
import static se.sundsvall.aotloader.configuration.ClockConfiguration.SWEDISH_TIME;

/**
 * Maps a candidate to what SupportManagement and the relation service get: the errand (section 7.3), its investigation
 * and the relation between an inspection errand and its permit errand (section 7.7.1). The post in PartyAssets is
 * {@link AssetMapper}'s.
 */
@Component
public class TargetMapper {

	private static final String INVESTIGATION_ACTIVE = "ACTIVE";
	private static final String INVESTIGATION_COMPLETED = "COMPLETED";
	private static final int INVESTIGATION_TITLE_MAX_LENGTH = 255;
	private static final String MEASURE_COMPLETED = "COMPLETED";
	private static final int MEASURE_TITLE_MAX_LENGTH = 255;
	private static final int MEASURE_DESCRIPTION_MAX_LENGTH = 3000;
	private static final String DECISION_DRAFT = "DRAFT";
	private static final String DECISION_COMPLETED = "COMPLETED";
	private static final String DECISION_MANUAL = "MANUAL";
	private static final int DECISION_TEXT_MAX_LENGTH = 255;

	private final AotLoaderProperties properties;
	private final Clock clock;
	private final StakeholderMapper stakeholders;

	public TargetMapper(final AotLoaderProperties properties, final Clock clock) {
		this.properties = properties;
		this.clock = clock;
		this.stakeholders = new StakeholderMapper(properties);
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
			.title(candidate.errand().title())
			.description(Texts.paragraphs(candidate.errand().description()))
			.priority(Priority.MEDIUM)
			.status(properties.errandStatus())
			.resolution(properties.errandResolution())
			.classification(new Classification().category(settings.category()).type(settings.type()))
			.labels(labels(candidate, labelIds))
			.channel(properties.channel())
			.businessRelated(true)
			.externalTags(externalTags(candidate))
			.stakeholders(stakeholders.stakeholders(candidate, parties))
			.parameters(ErrandParameterMapper.toParameters(Stream.of(candidate.errand().parameters(), premisesParameters(candidate), List.of(premisesMunicipalityParameter(candidate, municipalityId)))
				.flatMap(List::stream)
				.toList()));
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
	 *         inspection or trial was, with the times in Swedish time (decisions 41 and 46) and the texts of the source in
	 *         its conclusion (decisions 44–46), and with the template and fields of its inspection protocol when it has one
	 *         (decision 77); an end later than now, which the source has in error or for a planned inspection, leaves it
	 *         active without an end
	 */
	public Investigation toInvestigation(final InvestigationData investigation) {
		final var completedAt = untilNow(investigation.completedAt());
		return new Investigation()
			.status(completedAt == null ? INVESTIGATION_ACTIVE : INVESTIGATION_COMPLETED)
			.title(Texts.truncate(investigation.title(), INVESTIGATION_TITLE_MAX_LENGTH, null))
			.conclusion(conclusion(investigation.texts()))
			.startedAt(inSwedishTime(investigation.startedAt()))
			.completedAt(completedAt)
			.type(investigation.protocol())
			.parameters(investigation.protocolParameters().isEmpty() ? null
				: investigation.protocolParameters().stream()
					.map(parameter -> new generated.se.sundsvall.supportmanagement.Parameter().key(parameter.key()).displayName(parameter.displayName()).values(parameter.values()))
					.toList());
	}

	/**
	 * Maps the decision a permit was granted by to the decision of its errand, as Draken writes a manual decision on a
	 * permit (decision 70): of the type and with the heading of the errand type, an approval, with conditions when it has
	 * terms, made by the decision maker of the source, and with the parameters of the permit and of the decision under the
	 * keys Draken gives them. The terms and the documents are not part of it, since SupportManagement takes them through
	 * their own resources.
	 *
	 * @param  candidate       the permit, with its decision
	 * @param  legalBasis      the legal basis read from the documents of the permit, null when none was found
	 * @param  investigationId the investigation of the errand that the decision rests on, null when it has none
	 * @param  outcomeText     the display name of the outcome in the namespace, null when unknown
	 * @return                 the decision, as a draft
	 */
	public Decision toDecisionDraft(final Candidate candidate, final String legalBasis, final String investigationId, final String outcomeText) {
		final var decision = candidate.decision();
		final var settings = properties.errandType(candidate.type());
		return new Decision()
			.type(settings.decisionType())
			.status(DECISION_DRAFT)
			.title(Texts.truncate(settings.decisionTitle(), DECISION_TEXT_MAX_LENGTH, null))
			.description(decision.description())
			.outcome(decisionOutcome(decision))
			.method(DECISION_MANUAL)
			.decidedBy(Texts.truncate(decision.decidedBy(), DECISION_TEXT_MAX_LENGTH, null))
			.decidedAt(inSwedishTime(decision.decidedAt()))
			.legalBasis(AssetMapper.legalBasisWithin(Texts.trimToNull(legalBasis)))
			.justification(decision.justification())
			.validFrom(decision.validFrom())
			.validTo(decision.validTo())
			.investigationId(investigationId)
			.parameters(decisionParameters(candidate, outcomeText));
	}

	/**
	 * @return the change that completes the decision, at the time it was made
	 */
	public Decision toDecisionCompletion(final DecisionData decision) {
		return new Decision()
			.status(DECISION_COMPLETED)
			.completedAt(inSwedishTime(decision.decidedAt()));
	}

	/**
	 * @return the terms of the decision, numbered from 1 in their order, as Draken writes them
	 */
	public static List<DecisionTerm> toDecisionTerms(final DecisionData decision) {
		return IntStream.range(0, decision.terms().size())
			.mapToObj(index -> new DecisionTerm().sortOrder(index + 1).text(decision.terms().get(index)))
			.toList();
	}

	/**
	 * Tells whether a decision of the errand is the decision the migration would write now, so that it is kept as it is.
	 * Every field the migration writes is compared, the times as instants and the parameters by key, as well as the terms
	 * in their order and the attachments the decision links.
	 *
	 * @param  found       a decision of the errand, as SupportManagement gives it
	 * @param  draft       the decision as {@link #toDecisionDraft} gives it
	 * @param  completion  its completion, as {@link #toDecisionCompletion} gives it
	 * @param  terms       its terms, as {@link #toDecisionTerms} gives them
	 * @param  documentIds the attachments of the errand it is to link
	 * @return             true when the decision found is completed and has all of it, and links no other attachment
	 */
	public static boolean isWritten(final Decision found, final Decision draft, final Decision completion, final List<DecisionTerm> terms, final Collection<String> documentIds) {
		return DECISION_COMPLETED.equals(found.getStatus())
			&& Objects.equals(found.getType(), draft.getType())
			&& Objects.equals(found.getTitle(), draft.getTitle())
			&& Objects.equals(found.getDescription(), draft.getDescription())
			&& Objects.equals(found.getOutcome(), draft.getOutcome())
			&& Objects.equals(found.getMethod(), draft.getMethod())
			&& Objects.equals(found.getDecidedBy(), draft.getDecidedBy())
			&& sameInstant(found.getDecidedAt(), draft.getDecidedAt())
			&& sameInstant(found.getCompletedAt(), completion.getCompletedAt())
			&& Objects.equals(found.getLegalBasis(), draft.getLegalBasis())
			&& Objects.equals(found.getJustification(), draft.getJustification())
			&& Objects.equals(found.getValidFrom(), draft.getValidFrom())
			&& Objects.equals(found.getValidTo(), draft.getValidTo())
			&& Objects.equals(found.getInvestigationId(), draft.getInvestigationId())
			&& parameterValues(found.getParameters()).equals(parameterValues(draft.getParameters()))
			&& termTexts(found.getTerms()).equals(termTexts(terms))
			&& attachmentIds(found.getAttachments()).equals(Set.copyOf(documentIds));
	}

	private static boolean sameInstant(final OffsetDateTime one, final OffsetDateTime other) {
		return one == null ? other == null : other != null && one.isEqual(other);
	}

	private static Map<String, List<String>> parameterValues(final List<generated.se.sundsvall.supportmanagement.Parameter> parameters) {
		return Optional.ofNullable(parameters).orElse(List.of()).stream()
			.collect(Collectors.toMap(parameter -> Texts.trimToNull(parameter.getKey()), parameter -> Optional.ofNullable(parameter.getValues()).orElse(List.of()), (one, other) -> Stream
				.concat(one.stream(), other.stream()).toList()));
	}

	private static List<String> termTexts(final List<DecisionTerm> terms) {
		return Optional.ofNullable(terms).orElse(List.of()).stream()
			.sorted(Comparator.comparing(DecisionTerm::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())))
			.map(DecisionTerm::getText)
			.toList();
	}

	private static Set<String> attachmentIds(final List<ErrandAttachment> attachments) {
		return Optional.ofNullable(attachments).orElse(List.of()).stream().map(ErrandAttachment::getId).collect(Collectors.toSet());
	}

	/**
	 * @return an approval, with conditions when the decision has terms, as Draken gives it the outcome
	 */
	public static String decisionOutcome(final DecisionData decision) {
		return decision.terms().isEmpty() ? DECISION_OUTCOME_APPROVAL : DECISION_OUTCOME_APPROVAL_WITH_CONDITIONS;
	}

	/**
	 * The parameters Draken gives a decision on a permit for its certificate: those of the permit, but its legal basis and
	 * conditions, which the decision has as fields and terms, followed by the text of the outcome, the day of the decision
	 * and its first day. A parameter without a value is left out, as Draken leaves it out. Last come the decided ones,
	 * such as the beverages and the serving hours, with the labels and the group Draken gives them.
	 */
	private static List<generated.se.sundsvall.supportmanagement.Parameter> decisionParameters(final Candidate candidate, final String outcomeText) {
		final var decision = candidate.decision();
		final var values = new LinkedHashMap<String, String>();
		Optional.ofNullable(candidate.asset()).map(AssetData::parameters).orElse(Map.of()).forEach((key, value) -> {
			if (!ASSET_PARAMETER_LEGAL_BASIS.equals(key) && !ASSET_PARAMETER_CONDITIONS.equals(key)) {
				values.put(key, value);
			}
		});
		values.put(DECISION_PARAMETER_TEXT, outcomeText);
		values.put(DECISION_PARAMETER_DECISION_DATE, SourceValues.format(decision.decidedAt().toLocalDate()));
		values.put(DECISION_PARAMETER_VALID_FROM, SourceValues.format(decision.validFrom()));
		final var certificate = values.entrySet().stream()
			.filter(entry -> !Texts.isBlank(entry.getValue()))
			.map(entry -> new generated.se.sundsvall.supportmanagement.Parameter().key(entry.getKey())
				.values(List.of(Texts.truncate(entry.getValue().strip(), ERRAND_PARAMETER_MAX_LENGTH, null))));
		// What the permit was granted with, under the keys, labels and group Draken shows them by (decision 76)
		final var decided = decision.decided().stream()
			.map(parameter -> new generated.se.sundsvall.supportmanagement.Parameter().key(parameter.key()).displayName(parameter.displayName()).group(parameter.group())
				.values(parameter.values()));
		return Stream.concat(certificate, decided).toList();
	}

	/**
	 * @return the texts as HTML, each part with its heading in bold above its lines, since Draken shows no other text of
	 *         an investigation than its conclusion, in an editor that reads HTML; null without texts
	 */
	private static String conclusion(final InvestigationTexts texts) {
		final var html = texts.parts().stream()
			.map(part -> Texts.boldParagraph(part.heading()) + Texts.paragraphs(part.text()))
			.collect(Collectors.joining());
		return html.isEmpty() ? null : html;
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
		paths.addAll(candidate.errand().labelPaths());
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
		candidate.errand().externalTags().forEach((key, value) -> tags.add(new ExternalTag().key(key).value(value)));
		return tags;
	}

	/**
	 * The serving place or sales place as parameters of the errand, under the keys pw-alkt reads (section 7.2.1), followed
	 * by the place's own parameters, e.g. the restaurant number. A serving place of AlkT has the display names Draken
	 * writes, a sales place of AlkOL the same for a sales place, and the postal code is written without blanks, as Draken
	 * writes it.
	 */
	private static List<Parameter> premisesParameters(final Candidate candidate) {
		final var place = candidate.place();
		if (place == null) {
			return List.of();
		}
		final var premises = candidate.key().source() == Source.ALKT ? "Serveringsställets" : "Försäljningsställets";
		final var parameters = new ParameterList()
			.add(ERRAND_PARAMETER_PREMISES_NAME, premises + " namn", place.name())
			.add(ERRAND_PARAMETER_PREMISES_STREET_ADDRESS, premises + " gatuadress", place.address())
			.add(ERRAND_PARAMETER_PREMISES_POSTAL_CODE, premises + " postnummer", Texts.withoutBlanks(place.zipCode()))
			.add(ERRAND_PARAMETER_PREMISES_POSTAL_AREA, premises + " postort", place.city())
			.add(ERRAND_PARAMETER_PREMISES_PHONE, premises + " telefon", place.phone())
			.add(ERRAND_PARAMETER_PREMISES_EMAIL, premises + " e-post", place.email())
			.build();
		return Stream.concat(parameters.stream(), place.parameters().stream()).toList();
	}

	/**
	 * The municipality code of the place, on every errand whether it has a place or not (decision 19).
	 */
	private Parameter premisesMunicipalityParameter(final Candidate candidate, final String municipalityId) {
		return new Parameter(PARAMETER_PREMISES_MUNICIPALITY_ID, "Kommun", List.of(properties.premisesMunicipalityOf(candidate.place(), municipalityId)));
	}
}
