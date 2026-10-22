package se.sundsvall.aotloader.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.service.model.ServingPermitSubtype;
import se.sundsvall.aotloader.service.model.Source;

import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;
import static java.util.function.Predicate.not;
import static java.util.function.UnaryOperator.identity;

/**
 * The settings of the migration. Settings that depend on decisions not yet taken may be left blank: a dry run then
 * reports what is missing, and a real run refuses to start until it is set.
 *
 * @param namespace        the namespace in SupportManagement
 * @param sentBy           the value of the X-Sent-By header, which makes the migration the actor in the history of the
 *                         target systems
 * @param reporterUserId   the reporter of the migrated errands
 * @param channel          the channel of the migrated errands
 * @param errandStatus     the closed status the migrated errands get (decision 5)
 * @param errandResolution the resolution the migrated errands get, the one Draken closes an errand of AOT with
 * @param roles            the stakeholder roles
 * @param errandTypes      the settings per type of errand
 * @param databases        the source databases
 * @param fileArea         the directories of the file area per source
 * @param maxFileSize      the largest file uploaded (decision 24)
 * @param municipalities   the municipality of the places in an area, by the name of the area, for the areas that do not
 *                         belong to the municipality a run is started for. The errands are still created in that
 *                         municipality, with the municipality of the place as a parameter (decision 19).
 */
@Validated
@ConfigurationProperties("aot-loader")
public record AotLoaderProperties(
	@NotBlank String namespace,
	@NotBlank String sentBy,
	@NotBlank String reporterUserId,
	@NotBlank String channel,
	String errandStatus,
	@NotBlank String errandResolution,
	@NotNull @Valid Roles roles,
	Map<ErrandType, ErrandTypeSettings> errandTypes,
	Map<Source, Database> databases,
	Map<Source, FileAreaDirectories> fileArea,
	@NotNull DataSize maxFileSize,
	Map<String, String> municipalities) {

	public AotLoaderProperties {
		errandTypes = errandTypes == null ? emptyMap() : Map.copyOf(errandTypes);
		databases = databases == null ? emptyMap() : Map.copyOf(databases);
		fileArea = fileArea == null ? emptyMap() : Map.copyOf(fileArea);
		municipalities = municipalities == null ? emptyMap()
			: municipalities.entrySet().stream()
				.collect(Collectors.toUnmodifiableMap(entry -> areaKey(entry.getKey()), entry -> Optional.ofNullable(entry.getValue()).map(String::strip).orElse("")));
	}

	/**
	 * @param  area the name of the area of a place, as its code list gives it
	 * @return      the municipality of the area, empty when it belongs to the municipality a run is started for
	 */
	public Optional<String> municipalityOf(final String area) {
		return Optional.ofNullable(area).map(AotLoaderProperties::areaKey).map(municipalities::get);
	}

	/**
	 * @param  place          the place of an item, null when it has none
	 * @param  municipalityId the municipality the run is started for
	 * @return                the municipality code of the place: that of its area, or else the municipality of the run
	 */
	public String premisesMunicipalityOf(final Place place, final String municipalityId) {
		return Optional.ofNullable(place).map(Place::area).flatMap(this::municipalityOf).orElse(municipalityId);
	}

	private static String areaKey(final String area) {
		return area.strip().toLowerCase(Locale.ROOT);
	}

	/**
	 * @return the settings of the errand type, with every setting blank when none are configured
	 */
	public ErrandTypeSettings errandType(final ErrandType type) {
		return Optional.ofNullable(errandTypes.get(type)).orElse(new ErrandTypeSettings(null, null, null, null, null, null, null, null, false));
	}

	/**
	 * @return the database of the source, empty when it is not configured
	 */
	public Optional<Database> database(final Source source) {
		return Optional.ofNullable(databases.get(source)).filter(database -> database.url() != null && !database.url().isBlank());
	}

	/**
	 * @return the directories of the file area of the source, with both blank when none are configured
	 */
	public FileAreaDirectories fileArea(final Source source) {
		return Optional.ofNullable(fileArea.get(source)).orElse(new FileAreaDirectories(null, null));
	}

	/**
	 * The roles of the stakeholders, and the role the measures are added by.
	 *
	 * @param permitHolder     the permit holder, the role pw-alkt reads as holder
	 * @param contact          the persons with significant influence (decision 15), told apart from other contacts by the
	 *                         parameter PBI = true
	 * @param inspectionHolder the role of the holder an inspection errand concerned; PRIMARY would show the errand on Mina
	 *                         sidor (decision 27)
	 * @param measureAddedBy   the role a measure is added by, as Draken gives every measure it adds (decision 57)
	 */
	public record Roles(
		@NotBlank String permitHolder,
		@NotBlank String contact,
		String inspectionHolder,
		String measureAddedBy) {
	}

	/**
	 * The settings of a type of errand: what its errands become in the target systems and, for inspection errands, which
	 * errands of the source they are. Every errand also gets the label for migrated errands (decision 17), which is not
	 * among these settings.
	 *
	 * @param category             the category of the classification (decision 5)
	 * @param type                 the type of the classification (decision 5)
	 * @param measureType          for inspection visits, the measure type of the measures their remarks become (decision
	 *                             57)
	 * @param assetType            the type of the post in PartyAssets (decision 20)
	 * @param assetTitle           the title of the post in PartyAssets (decision 4)
	 * @param labels               the resource paths of the labels every errand of the type gets (section 7.3.4)
	 * @param subtypeLabels        for serving permits, the resource path of the label of each subtype (section 7.3.4)
	 * @param sourceErrandTypes    for inspection errands, the errand types in the source that are inspection errands, in
	 *                             lower case, with the resource path of the label each gets; a blank path means the label
	 *                             is not decided (section 7.7)
	 * @param onlyPlacesWithPermit for inspection errands and visits, true to migrate only those of places with a current
	 *                             permit
	 *                             (section 7.7)
	 */
	public record ErrandTypeSettings(
		String category,
		String type,
		String measureType,
		String assetType,
		String assetTitle,
		List<String> labels,
		Map<ServingPermitSubtype, String> subtypeLabels,
		Map<String, String> sourceErrandTypes,
		boolean onlyPlacesWithPermit) {

		public ErrandTypeSettings {
			labels = labels == null ? List.of() : labels.stream().filter(Objects::nonNull).map(String::strip).filter(not(String::isEmpty)).toList();
			subtypeLabels = strippedValues(subtypeLabels, identity());
			sourceErrandTypes = strippedValues(sourceErrandTypes, key -> key.strip().toLowerCase(Locale.ROOT));
		}

		/**
		 * @return the resource path of the label of the subtype, empty when it is not configured
		 */
		public Optional<String> subtypeLabel(final ServingPermitSubtype subtype) {
			return Optional.ofNullable(subtypeLabels.get(subtype)).filter(not(String::isEmpty));
		}

		/**
		 * @param  code the errand type in the source, in any case
		 * @return      the resource path of the label of the errand type, blank when it is not decided; empty when the
		 *              errand type is not an inspection errand of the settings
		 */
		public Optional<String> sourceErrandType(final String code) {
			return Optional.ofNullable(code).map(value -> sourceErrandTypes.get(value.strip().toLowerCase(Locale.ROOT)));
		}

		/**
		 * @return every resource path of a label in the settings
		 */
		public Set<String> allLabels() {
			final var paths = new LinkedHashSet<>(labels);
			Stream.concat(subtypeLabels.values().stream(), sourceErrandTypes.values().stream())
				.filter(not(String::isEmpty))
				.forEach(paths::add);
			return paths;
		}

		private static <K> Map<K, String> strippedValues(final Map<K, String> map, final UnaryOperator<K> keyMapper) {
			final var result = new LinkedHashMap<K, String>();
			Optional.ofNullable(map).orElse(emptyMap()).forEach((key, value) -> result.put(keyMapper.apply(key), value == null ? "" : value.strip()));
			return unmodifiableMap(result);
		}
	}

	/**
	 * A source database. Without username the driver logs in with the Windows account that runs the migration.
	 *
	 * @param url          the JDBC URL
	 * @param username     the user of SQL Server authentication, blank for Windows authentication
	 * @param password     the password of SQL Server authentication
	 * @param queryTimeout the longest time a query may take
	 */
	public record Database(String url, String username, String password, Duration queryTimeout) {

		public Database {
			queryTimeout = queryTimeout == null ? Duration.ofMinutes(2) : queryTimeout;
		}

		@Override
		public String toString() {
			return "Database[configured=" + (url != null && !url.isBlank()) + "]";
		}
	}

	/**
	 * The directories of a source on the file area.
	 *
	 * @param documents   the documents the system generated
	 * @param attachments the attached files
	 */
	public record FileAreaDirectories(String documents, String attachments) {

		@Override
		public String toString() {
			return "FileAreaDirectories[documents=" + (documents != null && !documents.isBlank()) + ", attachments=" + (attachments != null && !attachments.isBlank()) + "]";
		}
	}
}
