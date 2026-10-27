package se.sundsvall.aotloader.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.util.unit.DataSize;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.ErrandTypeSettings;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.FileAreaDirectories;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.Roles;
import se.sundsvall.aotloader.service.model.AssetData;
import se.sundsvall.aotloader.service.model.AssetFile;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.DecisionData;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.FilePurpose;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.InvestigationData;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.PermitLink;
import se.sundsvall.aotloader.service.model.Person;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.service.model.PlacePermit;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.service.model.ServingPermitSubtype;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.source.FilePurposes;

import static java.util.function.Function.identity;
import static se.sundsvall.aotloader.service.model.Kind.DOKUMENTATION;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYNSBESOK;
import static se.sundsvall.aotloader.service.model.PartyType.ENTERPRISE;
import static se.sundsvall.aotloader.service.model.PartyType.PRIVATE;

/**
 * Made up settings and candidates for the tests.
 */
public final class TestData {

	public static final LegalId HOLDER_ID = new LegalId("5566778899", ENTERPRISE);
	public static final LegalId PERSON_ID = new LegalId("198001011234", PRIVATE);
	public static final String INTERNAL_INSPECTION = "CATEGORYROOT/INSPECTION/INTERNAL_INSPECTION";
	public static final String EXTERNAL_INSPECTION = "CATEGORYROOT/INSPECTION/EXTERNAL_INSPECTION";
	public static final String DOCUMENTATION = "CATEGORYROOT/DOCUMENTATION/DOCUMENTATION";

	private static final List<FileReference> PERMIT_FILES = List.of(new FileReference("beslut.rtf", FileReference.FileFolder.DOCUMENTS),
		new FileReference("ansokan.pdf", FileReference.FileFolder.ATTACHMENTS));

	private static final Map<ErrandType, List<String>> LABELS = Map.ofEntries(
		Map.entry(ErrandType.TOBACCO_PERMIT, List.of("CATEGORYROOT/TOBACCO/SALES_PERMIT_APPLICATION")),
		Map.entry(ErrandType.FOLKOL_SALES_NOTIFICATION, List.of("CATEGORYROOT/ALCOHOL/FOLKOL_SALES_NOTIFICATION")),
		Map.entry(ErrandType.FOLKOL_SERVING_NOTIFICATION, List.of("CATEGORYROOT/ALCOHOL/FOLKOL_SERVING_NOTIFICATION")),
		Map.entry(ErrandType.ECIGARETTE_NOTIFICATION, List.of("CATEGORYROOT/TOBACCO/ECIGARETTE_SALES_NOTIFICATION")),
		Map.entry(ErrandType.TOBACCO_FREE_NICOTINE_NOTIFICATION, List.of("CATEGORYROOT/TOBACCO/TOBACCO_FREE_NICOTINE_SALES_NOTIFICATION")),
		Map.entry(ErrandType.ALCOHOL_INSPECTION, List.of("TAGROOT/ALCOHOL_INSPECTION")),
		Map.entry(ErrandType.TOBACCO_INSPECTION, List.of("TAGROOT/TOBACCO_INSPECTION")),
		Map.entry(ErrandType.ALCOHOL_INSPECTION_VISIT, List.of(EXTERNAL_INSPECTION, "TAGROOT/ALCOHOL_INSPECTION")),
		Map.entry(ErrandType.TOBACCO_INSPECTION_VISIT, List.of(EXTERNAL_INSPECTION, "TAGROOT/TOBACCO_INSPECTION")),
		Map.entry(ErrandType.ALCOHOL_DOCUMENTATION, List.of(DOCUMENTATION)),
		Map.entry(ErrandType.TOBACCO_DOCUMENTATION, List.of(DOCUMENTATION)));
	private static final Map<ServingPermitSubtype, String> SUBTYPE_LABELS = Arrays.stream(ServingPermitSubtype.values())
		.collect(Collectors.toMap(identity(), subtype -> "CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/" + subtype.name()));
	private static final Map<ErrandType, Map<String, String>> SOURCE_ERRAND_TYPES = Map.of(
		ErrandType.ALCOHOL_INSPECTION, Map.of("07", INTERNAL_INSPECTION),
		ErrandType.TOBACCO_INSPECTION, Map.of("it", INTERNAL_INSPECTION));

	private TestData() {}

	public static AotLoaderProperties properties() {
		return properties("documents", "attachments");
	}

	public static AotLoaderProperties properties(final String documents, final String attachments) {
		return properties(errandTypes(), documents, attachments, "mig01dec");
	}

	/**
	 * @return the settings of {@link #properties()}, except which errands of the source are inspection errands of the type
	 */
	public static AotLoaderProperties properties(final ErrandType inspectionType, final boolean onlyPlacesWithPermit, final Map<String, String> sourceErrandTypes) {
		final var types = errandTypes();
		final var settings = types.get(inspectionType);
		types.put(inspectionType, new ErrandTypeSettings(settings.category(), settings.type(), settings.measureType(), settings.assetType(), settings.assetTitle(), settings.labels(), settings.subtypeLabels(),
			sourceErrandTypes, onlyPlacesWithPermit, settings.decisionType(), settings.decisionTitle()));
		return properties(types, "documents", "attachments", "mig01dec");
	}

	/**
	 * @return the settings of {@link #properties(String, String)}, in which the serving permits get the decision they were
	 *         granted by, written with the AD account, null for none (decision 70)
	 */
	public static AotLoaderProperties propertiesWithDecisions(final String documents, final String attachments, final String decisionAdAccount) {
		final var types = errandTypes();
		final var settings = types.get(ErrandType.SERVING_PERMIT);
		types.put(ErrandType.SERVING_PERMIT, new ErrandTypeSettings(settings.category(), settings.type(), settings.measureType(), settings.assetType(), settings.assetTitle(), settings.labels(),
			settings.subtypeLabels(), settings.sourceErrandTypes(), settings.onlyPlacesWithPermit(), "SERVERINGSTILLSTAND", "Beslut om serveringstillstånd för alkoholdrycker"));
		return properties(types, documents, attachments, decisionAdAccount);
	}

	/**
	 * @return made up settings for every errand type, with the labels of the label tree in ALKT
	 */
	public static Map<ErrandType, ErrandTypeSettings> errandTypes() {
		final var types = new EnumMap<ErrandType, ErrandTypeSettings>(ErrandType.class);
		for (final var type : ErrandType.values()) {
			types.put(type, new ErrandTypeSettings("CATEGORY_" + type, "TYPE_" + type, type.getKind() == TILLSYNSBESOK ? "MEASURE_" + type : null, type.isWithAsset() ? "Asset" + type : null, type.isWithAsset() ? "Titel " + type : null,
				LABELS.getOrDefault(type, List.of()), type == ErrandType.SERVING_PERMIT ? SUBTYPE_LABELS : Map.of(), SOURCE_ERRAND_TYPES.getOrDefault(type, Map.of()), true, null, null));
		}
		return types;
	}

	private static AotLoaderProperties properties(final Map<ErrandType, ErrandTypeSettings> types, final String documents, final String attachments,
		final String decisionAdAccount) {
		return new AotLoaderProperties("AOT", "alkt-migration; type=migration", "alkt-migration", "MIGRATION", "SOLVED", "CLOSED",
			new Roles("PRIMARY", "CONTACT", "INSPECTED", "MEASURER"),
			types,
			Map.of(Source.ALKT, new AotLoaderProperties.Database("jdbc:sqlserver://alkt", null, null, null), Source.ALKOL, new AotLoaderProperties.Database("jdbc:sqlserver://alkol", null, null, null)),
			Map.of(Source.ALKT, new FileAreaDirectories(documents, attachments), Source.ALKOL, new FileAreaDirectories(documents, attachments)),
			DataSize.ofBytes(100),
			Map.of(" Timrå ", "2262"), decisionAdAccount);
	}

	public static Candidate permit(final String objektId) {
		return permit(objektId, "City");
	}

	public static Candidate permit(final String objektId, final String area) {
		return permitBuilder(objektId, area).files(PERMIT_FILES).build();
	}

	/**
	 * @return a permit with the rule hits the source showed
	 */
	public static Candidate permit(final String objektId, final RuleHit... hits) {
		return permitBuilder(objektId, "City").ruleHits(List.of(hits)).files(PERMIT_FILES).build();
	}

	/**
	 * @return a permit without files, whose migration uploads nothing, with the rule hits the source showed
	 */
	public static Candidate permitWithoutFiles(final String objektId, final RuleHit... hits) {
		return permitBuilder(objektId, "City").ruleHits(List.of(hits)).build();
	}

	/**
	 * @return a permit without files whose holder has the given persons with significant influence
	 */
	public static Candidate permitWithPersons(final String objektId, final Person... persons) {
		return permitBuilder(objektId, "City").persons(List.of(persons)).build();
	}

	/**
	 * @return a permit whose certificate, beslut.rtf, also goes on the post in PartyAssets, with the rule hits the source
	 *         showed
	 */
	public static Candidate permitWithCertificate(final String objektId, final RuleHit... hits) {
		return permitBuilder(objektId, "City")
			.ruleHits(List.of(hits))
			.files(PERMIT_FILES)
			.assetFiles(List.of(new AssetFile(new FileReference("beslut.rtf", FileReference.FileFolder.DOCUMENTS), "Tillståndsbevis")))
			.build();
	}

	/**
	 * @return a permit granted by a decision with a condition, whose document is the permit file beslut.rtf (decision 70)
	 */
	public static Candidate permitWithDecision(final String objektId) {
		return permitBuilder(objektId, "City")
			.files(PERMIT_FILES)
			.decision(new DecisionData("Anna Berg", LocalDateTime.of(2019, 4, 30, 9, 0), "Stadigvarande serveringstillstånd", "Tillstånd beviljas.", LocalDate.of(2019, 5, 1), null,
				List.of("Mat ska serveras"), List.of(PERMIT_FILES.getFirst())))
			.build();
	}

	/**
	 * @return a permit whose post reads its legal basis from the documents
	 */
	public static Candidate permitWithLegalBasisDocuments(final String objektId, final FileReference... documents) {
		final var permit = permit(objektId);
		final var asset = permit.asset();
		return permit.withAsset(new AssetData(asset.titleSuffix(), asset.description(), asset.issued(), asset.validTo(), asset.parameters(), List.of(documents)));
	}

	private static Candidate.Builder permitBuilder(final String objektId, final String area) {
		return Candidate.builder(new ItemKey(Source.ALKT, TILLSTAND, objektId), ErrandType.SERVING_PERMIT)
			.placeName("Restaurang " + objektId)
			.placeNumber("12345678")
			.holder(new Holder(HOLDER_ID, "Bolaget AB", "Gatan 1", "85230", "Sundsvall", "bolag@example.com", "060-1", "Kontakt Person"))
			.place(new Place("Restaurang " + objektId, "Storgatan 1", "85230", "Sundsvall", "060-2", null, area, List.of(new Parameter("restaurantNumber", "Valt restaurangnummer", List.of("12345678")))))
			.persons(List.of(new Person("Person Ett", PERSON_ID, false, List.of("Ledamot")), new Person("Utländsk Person", null, true, List.of())))
			.title("Serveringstillstånd – Restaurang " + objektId)
			.description("Beskrivning")
			.labelPath("CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/PERMANENT_SERVING")
			.externalTag("diarienummer", "IAN-2019-" + objektId)
			.parameters(List.of(new Parameter("alktObjektId", "Id i AlkT", List.of(objektId))))
			.asset(new AssetData(null, "Serveringstillstånd för Restaurang " + objektId, LocalDate.of(2019, 5, 1), null, Map.of("migreradFran", "AlkT")));
	}

	public static Candidate inspection(final String arendeId, final ItemKey permitKey, final LegalId inspectionHolder) {
		return inspection(arendeId, permitKey, HOLDER_ID, inspectionHolder);
	}

	public static Candidate inspection(final String arendeId, final ItemKey permitKey, final LegalId permitHolder, final LegalId inspectionHolder) {
		return inspection(arendeId, new PermitLink(permitKey == null ? null : new PlacePermit(permitKey, LocalDate.of(2019, 5, 1), null, true), permitHolder, inspectionHolder,
			LocalDate.of(2020, 1, 1)));
	}

	/**
	 * @return an inspection errand of 2020 whose relation to the permit the link decides
	 */
	public static Candidate inspection(final String arendeId, final PermitLink link) {
		return Candidate.builder(new ItemKey(Source.ALKT, TILLSYN, arendeId), ErrandType.ALCOHOL_INSPECTION)
			.placeName("Restaurang 1")
			.holder(new Holder(link.inspectionHolder(), "Bolaget AB", null, null, null, null, null, null))
			.title("Tillsyn – Restaurang 1")
			.investigation(new InvestigationData("Tillsyn – Restaurang 1", LocalDateTime.of(2020, 1, 1, 0, 0), LocalDateTime.of(2020, 3, 1, 0, 0), InvestigationTexts.NONE))
			.labelPath("TAGROOT/ALCOHOL_INSPECTION")
			.permitLink(link)
			.build();
	}

	/**
	 * @return the documentation errand of an event of place 1 with a text, filed under the permit or notification
	 *         (decision 71)
	 */
	public static Candidate documentation(final String handelseId, final ItemKey filedUnder) {
		return Candidate.builder(new ItemKey(Source.ALKT, DOKUMENTATION, handelseId), ErrandType.ALCOHOL_DOCUMENTATION)
			.placeName("Restaurang 1")
			.holder(new Holder(HOLDER_ID, "Bolaget AB", null, null, null, null, null, null))
			.title("Anteckning – Restaurang 1")
			.description("Anteckning på Restaurang 1 2024-03-01, som inte hörde till något ärende.\nSamtal med ägaren")
			.labelPath(DOCUMENTATION)
			.permitLink(PermitLink.filedUnder(new PlacePermit(filedUnder, null, null, true)))
			.build();
	}

	public static Map<String, String> labelIds() {
		return Map.of(
			"TAGROOT/MIGRATED_ERRAND", "label-migrated",
			"CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/PERMANENT_SERVING", "label-permanent-serving",
			"TAGROOT/ALCOHOL_INSPECTION", "label-alcohol-inspection",
			DOCUMENTATION, "label-documentation");
	}

	/**
	 * @return every purpose a file can get, with the id "purpose-" and the name, and the name in lower case as the name
	 *         shown
	 */
	public static Map<String, FilePurpose> purposes() {
		return FilePurposes.ALL.stream()
			.collect(Collectors.toMap(name -> name, name -> new FilePurpose("purpose-" + name, name, name.toLowerCase(Locale.ROOT))));
	}
}
