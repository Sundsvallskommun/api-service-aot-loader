package se.sundsvall.aotloader.service;

import generated.se.sundsvall.supportmanagement.AttachmentPurpose;
import generated.se.sundsvall.supportmanagement.Label;
import generated.se.sundsvall.supportmanagement.LabelAttribute;
import generated.se.sundsvall.supportmanagement.MeasureType;
import generated.se.sundsvall.supportmanagement.Role;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.ErrandTypeSettings;
import se.sundsvall.aotloader.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.aotloader.service.mapper.LabelPaths;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.FilePurpose;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.ServingPermitSubtype;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.source.FilePurposes;
import se.sundsvall.aotloader.source.SourceDatabases;
import se.sundsvall.aotloader.source.files.FileArea;
import se.sundsvall.aotloader.support.ErrorTexts;
import se.sundsvall.aotloader.support.Texts;

import static java.util.stream.Collectors.toCollection;
import static se.sundsvall.aotloader.Constants.LABEL_ATTRIBUTE_PROCESS_BLOCKED;
import static se.sundsvall.aotloader.Constants.LABEL_MIGRATED;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYNSBESOK;

/**
 * The checks before a run (section 9): what the configuration still waits for, that the file area and the source
 * databases are configured, that the account of each source database may only read, and in the namespace of the
 * municipality of the run that the labels exist, with the label for migrated errands blocking processes, and that no
 * action is active (R6). A real run does not start when a check fails; a dry run lists the problems and goes on.
 */
@Component
public class Preflight {

	private static final Pattern MUNICIPALITY_ID = Pattern.compile("\\d{4}");

	private final AotLoaderProperties properties;
	private final SupportManagementIntegration supportManagement;
	private final SourceDatabases sourceDatabases;

	Preflight(final AotLoaderProperties properties, final SupportManagementIntegration supportManagement, final SourceDatabases sourceDatabases) {
		this.properties = properties;
		this.supportManagement = supportManagement;
		this.sourceDatabases = sourceDatabases;
	}

	/**
	 * @param  municipalityId the municipality the run is started for
	 * @param  scope          the sources and kinds of the run
	 * @return                the problems found, and the ids of the labels and the attachment purposes of the namespace
	 *                        in the municipality of the run
	 */
	public Result check(final String municipalityId, final Map<Source, Set<Kind>> scope) {
		final var problems = new ArrayList<String>();
		final var types = errandTypes(scope);
		checkErrandStatus(problems);
		checkErrandTypes(types, problems);
		checkInspections(scope, problems);
		scope.keySet().forEach(source -> checkSource(source, problems));
		checkAreaMunicipalities(problems);
		final var labels = readLabels(municipalityId, problems);
		labels.ifPresent(found -> checkLabels(found, types, problems));
		checkActions(municipalityId, problems);
		checkMeasures(municipalityId, types, problems);
		final var purposes = readPurposes(municipalityId, problems);
		return new Result(problems, Map.of(municipalityId, labels.map(Preflight::labelIds).orElse(Map.of())), Map.of(municipalityId, purposes));
	}

	/**
	 * @return the errand types a run of the scope can create
	 */
	static Set<ErrandType> errandTypes(final Map<Source, Set<Kind>> scope) {
		return Arrays.stream(ErrandType.values())
			.filter(type -> scope.getOrDefault(type.getSource(), Set.of()).contains(type.getKind()))
			.collect(toCollection(() -> EnumSet.noneOf(ErrandType.class)));
	}

	private void checkErrandStatus(final List<String> problems) {
		if (Texts.isBlank(properties.errandStatus())) {
			problems.add("Den avslutade statusen för migrerade ärenden är inte angiven (aot-loader.errand-status, beslut 5)");
		}
	}

	private void checkErrandTypes(final Set<ErrandType> types, final List<String> problems) {
		types.forEach(type -> {
			final var settings = properties.errandType(type);
			if (Texts.isBlank(settings.category()) || Texts.isBlank(settings.type())) {
				problems.add("Klassificering saknas för %s (aot-loader.errand-types, beslut 5)".formatted(type));
			}
			if (type.isWithAsset() && Texts.isBlank(settings.assetType())) {
				problems.add("Typ i PartyAssets saknas för %s (aot-loader.errand-types, beslut 20)".formatted(type));
			}
			if (type.isWithAsset() && Texts.isBlank(settings.assetTitle())) {
				problems.add("Namn i PartyAssets saknas för %s (aot-loader.errand-types, beslut 4)".formatted(type));
			}
			if (type.getKind() == TILLSYNSBESOK && Texts.isBlank(settings.measureType())) {
				problems.add("Åtgärdstyp för anmärkningarna saknas för %s (aot-loader.errand-types, beslut 57)".formatted(type));
			}
			checkTypeLabel(type, settings, problems);
		});
	}

	/**
	 * Every errand is to get the label of its type: a serving permit that of its subtype, an inspection errand that of its
	 * errand type in the source, and the other errands one of the labels of their type.
	 */
	private static void checkTypeLabel(final ErrandType type, final ErrandTypeSettings settings, final List<String> problems) {
		if (type == ErrandType.SERVING_PERMIT) {
			Arrays.stream(ServingPermitSubtype.values())
				.filter(subtype -> settings.subtypeLabel(subtype).isEmpty())
				.forEach(subtype -> problems.add("Etiketten för undertypen %s saknas (aot-loader.errand-types, avsnitt 7.3.4)".formatted(subtype)));
		} else if (type.getKind() == TILLSYN) {
			final var source = type.getSource().getDisplayName();
			if (settings.sourceErrandTypes().isEmpty()) {
				problems.add("Inga ärendetyper i %s är angivna som tillsynsärenden (aot-loader.errand-types, avsnitt 7.7)".formatted(source));
			}
			settings.sourceErrandTypes().forEach((errandType, label) -> {
				if (label.isEmpty()) {
					problems.add("Etiketten för ärendetyp '%s' i %s är inte bestämd (aot-loader.errand-types, avsnitt 7.7)".formatted(errandType, source));
				}
			});
		} else if (settings.labels().isEmpty()) {
			problems.add("Etikett saknas för %s (aot-loader.errand-types, avsnitt 7.3.4)".formatted(type));
		}
	}

	private void checkInspections(final Map<Source, Set<Kind>> scope, final List<String> problems) {
		if (scope.values().stream().anyMatch(kinds -> kinds.contains(TILLSYN)) && Texts.isBlank(properties.roles().inspectionHolder())) {
			problems.add("Rollen för tillståndshavaren på tillsynsärenden är inte angiven (aot-loader.roles.inspection-holder, avsnitt 7.7)");
		}
	}

	private void checkSource(final Source source, final List<String> problems) {
		if (properties.database(source).isEmpty()) {
			problems.add("Källdatabasen för %s är inte konfigurerad".formatted(source.getDisplayName()));
		} else {
			checkReadOnly(source, problems);
		}
		final var fileArea = properties.fileArea(source);
		FileArea.checkDirectory(fileArea.documents()).ifPresent(problem -> problems.add("Filarean för %s, dokument: %s".formatted(source.getDisplayName(), problem)));
		FileArea.checkDirectory(fileArea.attachments()).ifPresent(problem -> problems.add("Filarean för %s, bilagor: %s".formatted(source.getDisplayName(), problem)));
	}

	private void checkReadOnly(final Source source, final List<String> problems) {
		try {
			sourceDatabases.writeAccess(source).ifPresent(access -> problems.add("Kontot för källdatabasen för %s %s, men källan får bara läsas (K7)".formatted(source.getDisplayName(), access)));
		} catch (final RuntimeException e) {
			problems.add("Kontots behörigheter i källdatabasen för %s gick inte att kontrollera: %s".formatted(source.getDisplayName(), ErrorTexts.describe(e)));
		}
	}

	/**
	 * The municipality codes of the areas that belong to another municipality, which the errands get as a parameter
	 * (decision 19).
	 */
	private void checkAreaMunicipalities(final List<String> problems) {
		properties.municipalities().forEach((area, municipality) -> {
			if (!MUNICIPALITY_ID.matcher(municipality).matches()) {
				problems.add("Kommunkoden '%s' för området '%s' är inte fyra siffror (aot-loader.municipalities, beslut 19)".formatted(municipality, area));
			}
		});
	}

	/**
	 * @return the labels of the namespace by normalized resource path; empty when they cannot be read, which is a problem
	 */
	private Optional<Map<String, Label>> readLabels(final String municipalityId, final List<String> problems) {
		try {
			final Map<String, Label> labels = new HashMap<>();
			flatten(supportManagement.getLabelTree(municipalityId), labels);
			return Optional.of(labels);
		} catch (final RuntimeException e) {
			problems.add("Etiketterna i SupportManagement gick inte att hämta: " + ErrorTexts.describe(e));
			return Optional.empty();
		}
	}

	/**
	 * Adds a problem when the remarks of the inspection visits cannot become measures (decision 57): the namespace lacks
	 * the role they are added by, or the measure type of a type of visit. SupportManagement requires both of every
	 * measure.
	 */
	private void checkMeasures(final String municipalityId, final Set<ErrandType> types, final List<String> problems) {
		final var visitTypes = types.stream().filter(type -> type.getKind() == TILLSYNSBESOK).toList();
		if (visitTypes.isEmpty()) {
			return;
		}
		checkMeasureRole(municipalityId, problems);
		// A blank measure type is reported with the settings of the errand type
		final var measureTypes = visitTypes.stream()
			.map(type -> properties.errandType(type).measureType())
			.filter(type -> !Texts.isBlank(type))
			.collect(toCollection(TreeSet::new));
		if (!measureTypes.isEmpty()) {
			checkMeasureTypes(municipalityId, measureTypes, problems);
		}
	}

	private void checkMeasureRole(final String municipalityId, final List<String> problems) {
		final var role = properties.roles().measureAddedBy();
		if (Texts.isBlank(role)) {
			problems.add("Rollen som tillsynsbesökens åtgärder läggs till med är inte angiven (aot-loader.roles.measure-added-by, beslut 57)");
			return;
		}
		try {
			if (supportManagement.getRoles(municipalityId).stream().map(Role::getName).noneMatch(role::equals)) {
				problems.add("Rollen %s finns inte i namnrymden, så tillsynsbesökens anmärkningar kan inte bli åtgärder (aot-loader.roles.measure-added-by, beslut 57)".formatted(role));
			}
		} catch (final RuntimeException e) {
			problems.add("Rollerna i SupportManagement gick inte att hämta: " + ErrorTexts.describe(e));
		}
	}

	private void checkMeasureTypes(final String municipalityId, final Set<String> measureTypes, final List<String> problems) {
		final Set<String> found;
		try {
			found = supportManagement.getMeasureTypes(municipalityId).stream().map(MeasureType::getName).collect(Collectors.toSet());
		} catch (final RuntimeException e) {
			problems.add("Åtgärdstyperna i SupportManagement gick inte att hämta: " + ErrorTexts.describe(e));
			return;
		}
		measureTypes.stream()
			.filter(type -> !found.contains(type))
			.forEach(type -> problems.add("Åtgärdstypen %s finns inte i namnrymden, så tillsynsbesökens anmärkningar kan inte bli åtgärder (beslut 57)".formatted(type)));
	}

	/**
	 * Reads the attachment purposes of the namespace, and adds a problem for each purpose a file can get that the namespace
	 * lacks or has deprecated (decision 40).
	 *
	 * @return the purposes a file can get, by name
	 */
	private Map<String, FilePurpose> readPurposes(final String municipalityId, final List<String> problems) {
		final List<AttachmentPurpose> found;
		try {
			found = supportManagement.getAttachmentPurposes(municipalityId);
		} catch (final RuntimeException e) {
			problems.add("Syftena för bilagor i SupportManagement gick inte att hämta: " + ErrorTexts.describe(e));
			return Map.of();
		}
		final var purposes = new HashMap<String, FilePurpose>();
		final var deprecated = new HashSet<String>();
		found.stream()
			.filter(purpose -> purpose.getId() != null && FilePurposes.ALL.contains(purpose.getName()))
			.forEach(purpose -> {
				if (Boolean.TRUE.equals(purpose.getDeprecated())) {
					deprecated.add(purpose.getName());
				} else {
					purposes.put(purpose.getName(), new FilePurpose(purpose.getId(), purpose.getName(), purpose.getDisplayName()));
				}
			});
		FilePurposes.ALL.stream()
			.filter(name -> !purposes.containsKey(name))
			.sorted()
			.forEach(name -> problems.add((deprecated.contains(name) ? "Syftet %s för bilagor är inaktuellt i namnrymden (beslut 40)" : "Syftet %s för bilagor finns inte i namnrymden (beslut 40)").formatted(name)));
		return purposes;
	}

	/**
	 * Adds a problem when an action is active in the namespace of the municipality (R6).
	 */
	private void checkActions(final String municipalityId, final List<String> problems) {
		// Activating an errand runs the active actions whatever its headers and labels say, and an action may mail the customer
		try {
			final var active = supportManagement.getActionConfigs(municipalityId).stream()
				.filter(config -> Boolean.TRUE.equals(config.getActive()))
				.map(config -> Optional.ofNullable(config.getName()).orElse("utan namn"))
				.toList();
			if (!active.isEmpty()) {
				problems.add("Åtgärder är aktiva i namespace %s och körs när ett migrerat ärende aktiveras: %s. De stängs av under körningen (R6, avsnitt 10.2)"
					.formatted(properties.namespace(), String.join(", ", active)));
			}
		} catch (final RuntimeException e) {
			problems.add("Åtgärderna i SupportManagement gick inte att hämta: " + ErrorTexts.describe(e));
		}
	}

	private void checkLabels(final Map<String, Label> labels, final Set<ErrandType> types, final List<String> problems) {
		requiredLabels(types).stream()
			.filter(path -> !labels.containsKey(LabelPaths.normalize(path)))
			.forEach(path -> problems.add("Etiketten %s finns inte i namespace %s".formatted(path, properties.namespace())));
		Optional.ofNullable(labels.get(LabelPaths.normalize(LABEL_MIGRATED)))
			.filter(label -> !blocksProcesses(label))
			.ifPresent(_ -> problems.add("Etiketten %s saknar attributet %s=true, som spärrar processer (beslut 17)".formatted(LABEL_MIGRATED, LABEL_ATTRIBUTE_PROCESS_BLOCKED)));
	}

	private Set<String> requiredLabels(final Set<ErrandType> types) {
		final var paths = new LinkedHashSet<String>();
		paths.add(LABEL_MIGRATED);
		types.forEach(type -> paths.addAll(properties.errandType(type).allLabels()));
		return paths;
	}

	private static Map<String, String> labelIds(final Map<String, Label> labels) {
		final var ids = new HashMap<String, String>();
		labels.forEach((path, label) -> Optional.ofNullable(label.getId()).ifPresent(id -> ids.put(path, id)));
		return ids;
	}

	private static boolean blocksProcesses(final Label label) {
		return Optional.ofNullable(label.getAttributes()).orElse(List.of()).stream()
			.filter(attribute -> attribute.getKey() != null && LABEL_ATTRIBUTE_PROCESS_BLOCKED.equalsIgnoreCase(attribute.getKey().strip()))
			.map(LabelAttribute::getValue)
			.anyMatch(value -> value != null && "true".equalsIgnoreCase(value.strip()));
	}

	private static void flatten(final List<Label> labels, final Map<String, Label> result) {
		Optional.ofNullable(labels).orElse(List.of()).forEach(label -> {
			Optional.ofNullable(LabelPaths.normalize(label.getResourcePath())).ifPresent(path -> result.putIfAbsent(path, label));
			flatten(label.getLabels(), result);
		});
	}

	/**
	 * What the checks found.
	 *
	 * @param problems what stops a real run
	 * @param labelIds the ids of the labels of the namespace by normalized resource path, per municipality
	 * @param purposes the attachment purposes of the namespace that a file can get, by name, per municipality
	 */
	public record Result(List<String> problems, Map<String, Map<String, String>> labelIds, Map<String, Map<String, FilePurpose>> purposes) {

		public Result {
			problems = List.copyOf(problems);
			labelIds = labelIds.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
			purposes = purposes.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
		}
	}
}
