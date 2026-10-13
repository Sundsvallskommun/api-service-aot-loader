package se.sundsvall.aotloader.service;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.unit.DataSize;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.ErrandTypeSettings;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.FileAreaDirectories;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.Inspection;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.Roles;
import se.sundsvall.aotloader.service.model.AssetData;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.PermitLink;
import se.sundsvall.aotloader.service.model.Person;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.service.model.Source;

import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYN;
import static se.sundsvall.aotloader.service.model.PartyType.ENTERPRISE;
import static se.sundsvall.aotloader.service.model.PartyType.PRIVATE;

/**
 * Made up settings and candidates for the tests of the service layer.
 */
public final class TestData {

	public static final LegalId HOLDER_ID = new LegalId("5566778899", ENTERPRISE);
	public static final LegalId PERSON_ID = new LegalId("198001011234", PRIVATE);

	private TestData() {}

	public static AotLoaderProperties properties() {
		return properties("documents", "attachments");
	}

	public static AotLoaderProperties properties(final String documents, final String attachments) {
		final var types = new EnumMap<ErrandType, ErrandTypeSettings>(ErrandType.class);
		for (final var type : ErrandType.values()) {
			types.put(type, new ErrandTypeSettings("CATEGORY_" + type, "TYPE_" + type, type.isWithAsset() ? "Asset" + type : null, type.isWithAsset() ? "Titel " + type : null));
		}
		return new AotLoaderProperties("ALKT", "alkt-migration; type=migration", "alkt-migration", "MIGRATION", "SOLVED",
			new Roles("PRIMARY", "CONTACT", "INSPECTED"),
			types,
			new Inspection(true, Map.of("07", "CATEGORYROOT/INSPECTION/INTERNAL_INSPECTION"), Map.of("it", "CATEGORYROOT/INSPECTION/INTERNAL_INSPECTION")),
			Map.of(Source.ALKT, new AotLoaderProperties.Database("jdbc:sqlserver://alkt", null, null, null), Source.ALKOL, new AotLoaderProperties.Database("jdbc:sqlserver://alkol", null, null, null)),
			Map.of(Source.ALKT, new FileAreaDirectories(documents, attachments), Source.ALKOL, new FileAreaDirectories(documents, attachments)),
			DataSize.ofBytes(100),
			Map.of(" Timrå ", "2262"));
	}

	public static Candidate permit(final String objektId) {
		return permit(objektId, "City");
	}

	public static Candidate permit(final String objektId, final String area) {
		return Candidate.builder(new ItemKey(Source.ALKT, TILLSTAND, objektId), ErrandType.SERVING_PERMIT)
			.placeName("Restaurang " + objektId)
			.placeNumber("12345678")
			.holder(new Holder(HOLDER_ID, "Bolaget AB", "Gatan 1", "85230", "Sundsvall", "bolag@example.com", "060-1", "Kontakt Person"))
			.place(new Place("Restaurang " + objektId, "Storgatan 1", "85230", "Sundsvall", "060-2", null, area, List.of(new Parameter("restaurantNumber", "Serveringsställenummer", List.of("12345678")))))
			.persons(List.of(new Person("Person Ett", PERSON_ID, List.of("Ledamot")), new Person("Utländsk Person", null, List.of())))
			.title("Serveringstillstånd – Restaurang " + objektId)
			.description("Beskrivning")
			.labelPath("CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/PERMANENT_SERVING")
			.externalTag("diarienummer", "IAN-2019-" + objektId)
			.parameters(List.of(new Parameter("alktObjektId", "AlkT-id", List.of(objektId))))
			.asset(new AssetData(null, "Serveringstillstånd för Restaurang " + objektId, LocalDate.of(2019, 5, 1), null, Map.of("migreradFran", "AlkT")))
			.files(List.of(new FileReference("beslut.rtf", FileReference.FileFolder.DOCUMENTS), new FileReference("ansokan.pdf", FileReference.FileFolder.ATTACHMENTS)))
			.build();
	}

	public static Candidate inspection(final String arendeId, final ItemKey permitKey, final LegalId inspectionHolder) {
		return Candidate.builder(new ItemKey(Source.ALKT, TILLSYN, arendeId), ErrandType.ALCOHOL_INSPECTION)
			.placeName("Restaurang 1")
			.holder(new Holder(inspectionHolder, "Bolaget AB", null, null, null, null, null, null))
			.title("Tillsyn – Restaurang 1")
			.labelPath("TAGROOT/ALCOHOL_INSPECTION")
			.permitLink(new PermitLink(permitKey, HOLDER_ID, inspectionHolder))
			.build();
	}

	public static Map<String, String> labelIds() {
		return Map.of(
			"TAGROOT/MIGRATED_ERRAND", "label-migrated",
			"CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/PERMANENT_SERVING", "label-permanent-serving",
			"TAGROOT/ALCOHOL_INSPECTION", "label-alcohol-inspection");
	}
}
