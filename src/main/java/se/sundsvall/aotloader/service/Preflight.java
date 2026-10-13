package se.sundsvall.aotloader.service;

import generated.se.sundsvall.supportmanagement.Label;
import generated.se.sundsvall.supportmanagement.LabelAttribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.ErrandTypeSettings;
import se.sundsvall.aotloader.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.aotloader.service.mapper.LabelPaths;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.ServingPermitSubtype;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.source.files.FileArea;
import se.sundsvall.aotloader.support.ErrorTexts;
import se.sundsvall.aotloader.support.Texts;

import static java.util.stream.Collectors.toCollection;
import static se.sundsvall.aotloader.Constants.LABEL_ATTRIBUTE_PROCESS_BLOCKED;
import static se.sundsvall.aotloader.Constants.LABEL_MIGRATED;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYN;

/**
 * The checks before a run (section 9): what the configuration still waits for, that the file area and the source
 * databases are configured, and in the namespace of every municipality the run may write to that the labels exist, with
 * the label for migrated errands blocking processes, and that no action is active (R6). A real run does not start when
 * a
 * check fails; a dry run lists the problems and goes on.
 */
@Component
public class Preflight {

	private static final Pattern MUNICIPALITY_ID = Pattern.compile("\\d{4}");

	private final AotLoaderProperties properties;
	private final SupportManagementIntegration supportManagement;

	Preflight(final AotLoaderProperties properties, final SupportManagementIntegration supportManagement) {
		this.properties = properties;
		this.supportManagement = supportManagement;
	}

	/**
	 * @param  municipalityId the municipality the run is started for
	 * @param  scope          the sources and kinds of the run
	 * @return                the problems found and the ids of the labels of the namespace in each municipality the run
	 *                        may write to
	 */
	public Result check(final String municipalityId, final Map<Source, Set<Kind>> scope) {
		final var problems = new ArrayList<String>();
		final var types = errandTypes(scope);
		checkErrandStatus(problems);
		checkErrandTypes(types, problems);
		checkInspections(scope, problems);
		scope.keySet().forEach(source -> checkSource(source, problems));
		final var labelIds = new HashMap<String, Map<String, String>>();
		for (final var municipality : municipalities(municipalityId, problems)) {
			final var forMunicipality = municipality.equals(municipalityId) ? "" : " för kommun " + municipality;
			final var labels = readLabels(municipality, forMunicipality, problems);
			labels.ifPresent(found -> checkLabels(found, forMunicipality, types, problems));
			checkActions(municipality, forMunicipality, problems);
			labelIds.put(municipality, labels.map(Preflight::labelIds).orElse(Map.of()));
		}
		return new Result(problems, labelIds);
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
		}
		final var fileArea = properties.fileArea(source);
		FileArea.checkDirectory(fileArea.documents()).ifPresent(problem -> problems.add("Filarean för %s, dokument: %s".formatted(source.getDisplayName(), problem)));
		FileArea.checkDirectory(fileArea.attachments()).ifPresent(problem -> problems.add("Filarean för %s, bilagor: %s".formatted(source.getDisplayName(), problem)));
	}

	/**
	 * @return the municipality the run is started for, and those of the areas that belong to another one (decision 19)
	 */
	private Set<String> municipalities(final String municipalityId, final List<String> problems) {
		final var municipalities = new LinkedHashSet<String>();
		municipalities.add(municipalityId);
		properties.municipalities().forEach((area, municipality) -> {
			if (!MUNICIPALITY_ID.matcher(municipality).matches()) {
				problems.add("Kommunkoden '%s' för området '%s' är inte fyra siffror (aot-loader.municipalities, beslut 19)".formatted(municipality, area));
			} else if (municipality.equals(municipalityId)) {
				// Every item outside the areas would then go to the municipality of the area, and stay there
				problems.add("Körningen är startad för kommun %s, som bara ska ha ställena i området '%s' (beslut 19)".formatted(municipality, area));
			} else {
				municipalities.add(municipality);
			}
		});
		return municipalities;
	}

	/**
	 * @return the labels of the namespace by normalized resource path; empty when they cannot be read, which is a problem
	 */
	private Optional<Map<String, Label>> readLabels(final String municipalityId, final String forMunicipality, final List<String> problems) {
		try {
			final Map<String, Label> labels = new HashMap<>();
			flatten(supportManagement.getLabelTree(municipalityId), labels);
			return Optional.of(labels);
		} catch (final RuntimeException e) {
			problems.add("Etiketterna i SupportManagement%s gick inte att hämta: %s".formatted(forMunicipality, ErrorTexts.describe(e)));
			return Optional.empty();
		}
	}

	/**
	 * Adds a problem when an action is active in the namespace of the municipality (R6).
	 */
	private void checkActions(final String municipalityId, final String forMunicipality, final List<String> problems) {
		// Activating an errand runs the active actions whatever its headers and labels say, and an action may mail the customer
		try {
			final var active = supportManagement.getActionConfigs(municipalityId).stream()
				.filter(config -> Boolean.TRUE.equals(config.getActive()))
				.map(config -> Optional.ofNullable(config.getName()).orElse("utan namn"))
				.toList();
			if (!active.isEmpty()) {
				problems.add("Åtgärder är aktiva i namespace %s%s och körs när ett migrerat ärende aktiveras: %s. De stängs av under körningen (R6, avsnitt 10.2)"
					.formatted(properties.namespace(), forMunicipality, String.join(", ", active)));
			}
		} catch (final RuntimeException e) {
			problems.add("Åtgärderna i SupportManagement%s gick inte att hämta: %s".formatted(forMunicipality, ErrorTexts.describe(e)));
		}
	}

	private void checkLabels(final Map<String, Label> labels, final String forMunicipality, final Set<ErrandType> types, final List<String> problems) {
		requiredLabels(types).stream()
			.filter(path -> !labels.containsKey(LabelPaths.normalize(path)))
			.forEach(path -> problems.add("Etiketten %s finns inte i namespace %s%s".formatted(path, properties.namespace(), forMunicipality)));
		Optional.ofNullable(labels.get(LabelPaths.normalize(LABEL_MIGRATED)))
			.filter(label -> !blocksProcesses(label))
			.ifPresent(_ -> problems.add("Etiketten %s%s saknar attributet %s=true, som spärrar processer (beslut 17)".formatted(LABEL_MIGRATED, forMunicipality, LABEL_ATTRIBUTE_PROCESS_BLOCKED)));
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
	 */
	public record Result(List<String> problems, Map<String, Map<String, String>> labelIds) {

		public Result {
			problems = List.copyOf(problems);
			labelIds = labelIds.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
		}
	}
}
