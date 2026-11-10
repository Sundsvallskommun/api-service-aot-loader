package se.sundsvall.aotloader.service.mapper;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetJsonParameter;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.partyassets.Status;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.service.TestData;
import se.sundsvall.aotloader.service.model.AssetData;
import se.sundsvall.aotloader.service.model.AssetSchema;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.JsonContent;
import se.sundsvall.aotloader.service.model.JsonParameterType;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.support.JsonFields;
import se.sundsvall.aotloader.support.MigrationException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static se.sundsvall.aotloader.service.model.Kind.ANMALAN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;

class AssetMapperTest {

	private static final String ERRAND_ID = "11111111-1111-1111-1111-111111111111";
	private static final String MUNICIPALITY_ID = "2281";
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final AssetMapper mapper = new AssetMapper(TestData.properties());

	@Test
	void toAsset() {
		final var asset = mapper.toAsset(TestData.permit("1"), MUNICIPALITY_ID, ERRAND_ID, "party-id", false, null);

		assertThat(asset.getAssetId()).isEqualTo(ERRAND_ID);
		assertThat(asset.getOrigin()).isEqualTo("SUPPORTMANAGEMENT");
		assertThat(asset.getPartyId()).isEqualTo("party-id");
		assertThat(asset.getType()).isEqualTo("AssetSERVING_PERMIT");
		assertThat(asset.getTitle()).isEqualTo("Titel SERVING_PERMIT");
		assertThat(asset.getDescription()).isEqualTo("Serveringstillstånd för Restaurang 1");
		assertThat(asset.getIssued()).isEqualTo(LocalDate.of(2019, 5, 1));
		assertThat(asset.getValidTo()).isNull();
		assertThat(asset.getStatus()).isEqualTo(Status.ACTIVE);
		assertThat(asset.getStatusReason()).isNull();
		// The permit is kept in the JSON parameter only (decision 87)
		assertThat(asset.getAdditionalParameters()).isNull();
		assertThat(asset.getJsonParameters()).singleElement().satisfies(parameter -> {
			assertThat(parameter.getKey()).isEqualTo("aot_alcohol_party_asset");
			assertThat(parameter.getSchemaId()).isEqualTo("2281_aot_alcohol_party_asset_0.1");
			// The id of the errand comes first, and a post without a place gets the municipality all the same
			assertThat(JSON.writeValueAsString(parameter.getValue())).isEqualTo("""
				{"errandId":"11111111-1111-1111-1111-111111111111","caseRegistrationNumber":"IAN-2019-1","externalCaseRegistrationNumber":"IAN-2019-1",\
				"migration":{"sourceSystem":"ALKT","issuedDateEstimated":false},"premises":{"municipalityId":"2281"}}""");
		});
	}

	@Test
	void aPlaceInAnAreaOfAnotherMunicipalityHasThatMunicipalityOnThePost() {
		final var asset = mapper.toAsset(TestData.permit("1", "timrå"), MUNICIPALITY_ID, ERRAND_ID, "party-id", false, null);

		assertThat(asset.getJsonParameters().getFirst().getValue().get("premises").get("municipalityId").asString()).isEqualTo("2262");
	}

	@Test
	void thePlaceAndTheDecisionGetWhatComesWhenThePostIsWritten() {
		final var content = JsonFields.object()
			.object("pointOfSale", JsonFields.object().text("name", "Kiosken"))
			.object("decision", JsonFields.object().text("outcome", "APPROVAL"))
			.toNode();
		final var legalBasis = "5 kap. 1 § lagen (2018:2088) om tobak och liknande produkter";

		final var value = AssetMapper.jsonValue(content, AssetSchema.TOBACCO, legalBasis, ERRAND_ID, "2262", " Bifall ");

		assertThat(JSON.writeValueAsString(value)).isEqualTo("""
			{"errandId":"11111111-1111-1111-1111-111111111111","pointOfSale":{"name":"Kiosken","municipalityId":"2262"},\
			"decision":{"outcome":"APPROVAL","decisionText":"Bifall","legalBasis":"5 kap. 1 § lagen (2018:2088) om tobak och liknande produkter"}}""");
		// What the source gives is not changed by it
		assertThat(content.get("pointOfSale").has("municipalityId")).isFalse();
	}

	@Test
	void aLegalBasisOfAPostWithoutADecisionGetsADecisionOfItsOwn() {
		final var content = JsonNodeFactory.instance.objectNode();

		final var value = AssetMapper.jsonValue(content, AssetSchema.ALCOHOL, "8 kap. 2 § alkohollagen (2010:1622)", ERRAND_ID, MUNICIPALITY_ID, "Bifall");

		// Without a decision there is no outcome to name
		assertThat(JSON.writeValueAsString(value.get("decision"))).isEqualTo("""
			{"legalBasis":"8 kap. 2 § alkohollagen (2010:1622)"}""");
		assertThat(AssetMapper.jsonValue(content, AssetSchema.ALCOHOL, " ", ERRAND_ID, MUNICIPALITY_ID, "Bifall").has("decision")).isFalse();
	}

	@Test
	void theDecisionSaysOfThePermitWhatThePostDoesByTheDecisionSchemaOfItsArea() {
		final var candidate = TestData.permitWithDecision("1");
		final var withLegalBasis = candidate.withAsset(candidate.asset().withLegalBasis("8 kap. 2 § alkohollagen (2010:1622)"));

		final var content = mapper.toDecisionContent(withLegalBasis, MUNICIPALITY_ID, ERRAND_ID, "Bifall med villkor");

		// What the decision says, with what the post gets when it is written: the errand, the municipality, the text of the
		// outcome and the legal basis; by the decision schema without the diary number of the errand that granted the permit
		assertThat(content).hasValueSatisfying(json -> {
			assertThat(json.type()).isEqualTo(JsonParameterType.ALCOHOL_DECISION);
			assertThat(JSON.writeValueAsString(json.value())).isEqualTo("""
				{"errandId":"11111111-1111-1111-1111-111111111111","externalCaseRegistrationNumber":"IAN-2019-1",\
				"migration":{"sourceSystem":"ALKT","issuedDateEstimated":false},"beverages":["STRONG_BEER","WINE"],\
				"decision":{"caseRegistrationNumber":"IAN-2019-1","outcome":"APPROVAL_WITH_CONDITIONS","decisionDate":"2019-04-30","decisionText":"Bifall med villkor",\
				"legalBasis":"8 kap. 2 § alkohollagen (2010:1622)"},"premises":{"municipalityId":"2281"}}""");
		});
		final var tobacco = Candidate.builder(new ItemKey(Source.ALKOL, TILLSTAND, "1"), ErrandType.TOBACCO_PERMIT)
			.asset(new AssetData(null, "Tobakstillstånd", null, null, AssetSchema.TOBACCO, null, List.of()))
			.decision(candidate.decision())
			.build();
		assertThat(mapper.toDecisionContent(tobacco, MUNICIPALITY_ID, ERRAND_ID, null)).map(JsonContent::type).contains(JsonParameterType.TOBACCO_DECISION);
	}

	@Test
	void theDecisionIsRegisteredUnderTheDiaryNumberOfItsOwnErrandOrElseUnderThatOfThePost() {
		final var candidate = TestData.permitWithDecision("1");
		final Function<JsonFields, Candidate> deciding = decision -> Candidate.builder(candidate.key(), candidate.type())
			.asset(candidate.asset())
			.decision(candidate.decision().withContent(JsonFields.object()
				.text("caseRegistrationNumber", "IAN-2019-1")
				.text("externalCaseRegistrationNumber", "IAN-2019-1")
				.object("decision", decision)
				.toNode()))
			.build();

		final var ownErrand = mapper.toDecisionContent(deciding.apply(JsonFields.object().text("caseRegistrationNumber", "IAN-2020-7")), MUNICIPALITY_ID, ERRAND_ID, null);
		final var noErrand = mapper.toDecisionContent(deciding.apply(JsonFields.object().text("outcome", "APPROVAL")), MUNICIPALITY_ID, ERRAND_ID, null);

		assertThat(ownErrand).hasValueSatisfying(json -> {
			assertThat(json.value().has("caseRegistrationNumber")).isFalse();
			assertThat(json.value().get("externalCaseRegistrationNumber").asString()).isEqualTo("IAN-2020-7");
			assertThat(json.value().get("decision").get("caseRegistrationNumber").asString()).isEqualTo("IAN-2020-7");
		});
		assertThat(noErrand).hasValueSatisfying(json -> {
			assertThat(json.value().has("caseRegistrationNumber")).isFalse();
			assertThat(json.value().get("externalCaseRegistrationNumber").asString()).isEqualTo("IAN-2019-1");
		});
	}

	@Test
	void aDecisionThatSaysNothingOfThePermitGetsNoJson() {
		final var candidate = TestData.permitWithDecision("1");

		assertThat(mapper.toDecisionContent(TestData.permit("1"), MUNICIPALITY_ID, ERRAND_ID, null)).isEmpty();
		assertThat(mapper.toDecisionContent(candidate.withAsset(null), MUNICIPALITY_ID, ERRAND_ID, null)).isEmpty();
		assertThat(mapper.toDecisionContent(Candidate.builder(candidate.key(), candidate.type()).asset(candidate.asset())
			.decision(candidate.decision().withContent(null)).build(), MUNICIPALITY_ID, ERRAND_ID, null)).isEmpty();
	}

	@Test
	void toAssetShortensTheDescriptionAndAddsTheTitleSuffix() {
		final var candidate = Candidate.builder(new ItemKey(Source.ALKOL, TILLSTAND, "1"), ErrandType.TOBACCO_PERMIT)
			.asset(new AssetData("detaljhandel", "Tobakstillstånd för " + "b".repeat(300), LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1), AssetSchema.TOBACCO,
				JsonFields.object().texts("conditions", List.of("v".repeat(300))).toNode(), List.of()))
			.build();

		final var asset = mapper.toAsset(candidate, MUNICIPALITY_ID, ERRAND_ID, "party-id", true, null);

		assertThat(asset.getTitle()).isEqualTo("Titel TOBACCO_PERMIT – detaljhandel");
		assertThat(asset.getStatus()).isEqualTo(Status.EXPIRED);
		assertThat(asset.getValidTo()).isEqualTo(LocalDate.of(2030, 1, 1));
		assertThat(asset.getDescription()).hasSize(255).startsWith("Tobakstillstånd för b").endsWith(" … (se ärendet)");
		// The JSON parameter has no limit of length
		assertThat(asset.getJsonParameters().getFirst().getKey()).isEqualTo("aot_tobacco_party_asset");
		assertThat(asset.getJsonParameters().getFirst().getValue().get("conditions").get(0).asString()).hasSize(300);
	}

	@Test
	void toAssetOfAnErrandWithoutAsset() {
		final var candidate = Candidate.builder(new ItemKey(Source.ALKOL, ANMALAN, "1-FOLKOL"), ErrandType.FOLKOL_SALES_NOTIFICATION).build();

		assertThatThrownBy(() -> mapper.toAsset(candidate, MUNICIPALITY_ID, ERRAND_ID, "party-id", false, null)).isInstanceOf(MigrationException.class);
	}

	@Test
	void toContentUpdate() {
		final var wanted = mapper.toAsset(TestData.permit("1"), MUNICIPALITY_ID, ERRAND_ID, "party-id", false, null).validTo(LocalDate.of(2027, 6, 30));
		final var same = new Asset().title(wanted.getTitle()).validTo(wanted.getValidTo()).jsonParameters(wanted.getJsonParameters()).additionalParameters(Map.of());

		assertThat(mapper.toContentUpdate(same, wanted)).isEmpty();
		assertThat(mapper.toContentUpdate(same.type("Annan typ").description("Annan beskrivning"), wanted)).isEmpty();
		assertThat(mapper.toContentUpdate(new Asset().title("Gammal").validTo(wanted.getValidTo()).jsonParameters(wanted.getJsonParameters()), wanted))
			.contains(new AssetUpdateRequest().title(wanted.getTitle()));
		assertThat(mapper.toContentUpdate(new Asset().title(wanted.getTitle()).jsonParameters(wanted.getJsonParameters()), wanted))
			.contains(new AssetUpdateRequest().validTo(LocalDate.of(2027, 6, 30)));
		// PartyAssets replaces the whole list, so every parameter is sent
		assertThat(mapper.toContentUpdate(new Asset().title(wanted.getTitle()).validTo(wanted.getValidTo()), wanted))
			.contains(new AssetUpdateRequest().jsonParameters(wanted.getJsonParameters()));
		// The additional parameters an earlier version of the migration wrote are removed
		assertThat(mapper.toContentUpdate(new Asset().title(wanted.getTitle()).validTo(wanted.getValidTo()).jsonParameters(wanted.getJsonParameters())
			.additionalParameters(Map.of("errandId", ERRAND_ID)), wanted))
			.contains(new AssetUpdateRequest().additionalParameters(Map.of()));

		final var withoutTitleOrLastDay = new AssetCreateRequest();
		assertThat(mapper.toContentUpdate(new Asset().title("Gammal").validTo(LocalDate.of(2020, 1, 1)), withoutTitleOrLastDay))
			.contains(new AssetUpdateRequest().title("").indefinitely(true));
	}

	@Test
	void theJsonParametersAreTheSameWhateverTheTypeOfTheirNumbersAndTheOrderOfTheirFields() {
		final var factory = JsonNodeFactory.instance;
		final var sent = new AssetJsonParameter().key("key").schemaId("schema").value(factory.objectNode().put("a", "x").put("seatsIndoor", 80));
		final var readBack = new AssetJsonParameter().key("key").schemaId("schema").value(factory.objectNode().set("seatsIndoor", factory.numberNode(80L)).put("a", "x"));
		final var other = new AssetJsonParameter().key("key").schemaId("schema").value(factory.objectNode().put("a", "x").put("seatsIndoor", 81));

		assertThat(AssetMapper.sameJsonParameters(List.of(readBack), List.of(sent))).isTrue();
		assertThat(AssetMapper.sameJsonParameters(null, List.of())).isTrue();
		assertThat(AssetMapper.sameJsonParameters(List.of(other), List.of(sent))).isFalse();
		assertThat(AssetMapper.sameJsonParameters(List.of(sent.schemaId("another")), List.of(readBack))).isFalse();
		assertThat(AssetMapper.sameJsonParameters(List.of(), List.of(readBack))).isFalse();
		assertThat(AssetMapper.sameJsonParameters(List.of(new AssetJsonParameter().key("key").schemaId("schema")), List.of(readBack))).isFalse();
	}

	@Test
	void sourceReference() {
		assertThat(mapper.toSourceReference(ERRAND_ID)).isEqualTo("LINK|" + ERRAND_ID + ";case;supportmanagement;AOT|");
	}
}
