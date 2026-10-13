package se.sundsvall.aotloader.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.Source;

import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;

/**
 * The settings of the migration. Settings that depend on decisions not yet taken may be left blank: a dry run then
 * reports what is missing, and a real run refuses to start until it is set.
 *
 * @param namespace      the namespace in SupportManagement
 * @param sentBy         the value of the X-Sent-By header, which makes the migration the actor in the history of the
 *                       target systems
 * @param reporterUserId the reporter of the migrated errands
 * @param channel        the channel of the migrated errands
 * @param errandStatus   the closed status the migrated errands get (decision 5)
 * @param roles          the stakeholder roles
 * @param errandTypes    the settings per type of errand
 * @param inspection     the settings of the inspection errands
 * @param databases      the source databases
 * @param fileArea       the directories of the file area per source
 * @param maxFileSize    the largest file uploaded (decision 24)
 * @param municipalities the municipality of the places in an area, by the name of the area, for the areas that do not
 *                       belong to the municipality a run is started for (decision 19)
 */
@Validated
@ConfigurationProperties("aot-loader")
public record AotLoaderProperties(
	@NotBlank String namespace,
	@NotBlank String sentBy,
	@NotBlank String reporterUserId,
	@NotBlank String channel,
	String errandStatus,
	@NotNull @Valid Roles roles,
	Map<ErrandType, ErrandTypeSettings> errandTypes,
	@NotNull @Valid Inspection inspection,
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

	private static String areaKey(final String area) {
		return area.strip().toLowerCase(Locale.ROOT);
	}

	/**
	 * @return the settings of the errand type, with every setting blank when none are configured
	 */
	public ErrandTypeSettings errandType(final ErrandType type) {
		return Optional.ofNullable(errandTypes.get(type)).orElse(new ErrandTypeSettings(null, null, null, null));
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
	 * The roles of the stakeholders.
	 *
	 * @param permitHolder     the permit holder, the role pw-alkt reads as holder
	 * @param contact          the persons with significant influence (decision 15), told apart from other contacts by the
	 *                         parameter PBI = true
	 * @param inspectionHolder the holder an inspection errand concerned (section 7.7, item 5)
	 */
	public record Roles(
		@NotBlank String permitHolder,
		@NotBlank String contact,
		String inspectionHolder) {
	}

	/**
	 * The settings of a type of errand.
	 *
	 * @param category   the category of the classification (decision 5)
	 * @param type       the type of the classification (decision 5)
	 * @param assetType  the type of the post in PartyAssets (decision 20)
	 * @param assetTitle the title of the post in PartyAssets (decision 4)
	 */
	public record ErrandTypeSettings(String category, String type, String assetType, String assetTitle) {
	}

	/**
	 * The settings of the inspection errands.
	 *
	 * @param onlyPlacesWithPermit true to migrate only the inspection errands of places with a current permit
	 * @param alktErrandTypes      the errand types in AlkT that are inspection errands, with the resource path of the
	 *                             label each gets; a blank path means the label is not decided
	 * @param alkolErrandTypes     the same for AlkOL
	 */
	public record Inspection(
		boolean onlyPlacesWithPermit,
		Map<String, String> alktErrandTypes,
		Map<String, String> alkolErrandTypes) {

		public Inspection {
			alktErrandTypes = lowerCaseKeys(alktErrandTypes);
			alkolErrandTypes = lowerCaseKeys(alkolErrandTypes);
		}

		/**
		 * @return the errand types of the source that are inspection errands, in lower case, with their label paths
		 */
		public Map<String, String> errandTypes(final Source source) {
			return source == Source.ALKT ? alktErrandTypes : alkolErrandTypes;
		}

		private static Map<String, String> lowerCaseKeys(final Map<String, String> map) {
			final var result = new LinkedHashMap<String, String>();
			Optional.ofNullable(map).orElse(emptyMap()).forEach((key, value) -> result.put(key.strip().toLowerCase(Locale.ROOT), value == null ? "" : value.strip()));
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
			return "FileAreaDirectories[configured=" + (documents != null && !documents.isBlank()) + "]";
		}
	}
}
