package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.service.model.AssetSchema;
import se.sundsvall.aotloader.service.model.DecisionData;
import se.sundsvall.aotloader.service.model.Place;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AssetContentsTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final String WITH_DELEGATION = "MBN/2019:123 MBN DEL/2019 §45";
	private static final LocalDate ISSUED = LocalDate.of(2019, 5, 2);

	private static DecisionData decision(final String caseNumber) {
		return new DecisionData("Handläggare", null, LocalDateTime.of(2019, 4, 30, 9, 0), null, null, null, null, List.of(), List.of()).withCase(caseNumber, null);
	}

	private static String json(final AssetContents contents) {
		return JSON.writeValueAsString(contents.fields().toNode());
	}

	@Test
	void theReferenceToADelegationDecisionIsTheDelegationReferenceOfTheDecision() {
		final var contents = new AssetContents(AssetSchema.TOBACCO).caseRegistrationNumber(WITH_DELEGATION).decision(decision(WITH_DELEGATION), ISSUED, null);

		assertThat(json(contents)).isEqualTo("""
			{"caseRegistrationNumber":"MBN/2019:123","externalCaseRegistrationNumber":"MBN/2019:123",\
			"decision":{"caseRegistrationNumber":"MBN/2019:123","outcome":"APPROVAL","decisionDate":"2019-04-30","executionDate":"2019-05-02",\
			"delegationReference":"MBN DEL/2019 §45"}}""");
	}

	@Test
	void aReferenceOnlyOnTheDecisionLeavesTheNumberOfThePermitAsItIs() {
		final var contents = new AssetContents(AssetSchema.ALCOHOL).caseRegistrationNumber("MBN/2019:99").decision(decision(WITH_DELEGATION), ISSUED, null);

		assertThat(json(contents)).isEqualTo("""
			{"caseRegistrationNumber":"MBN/2019:99","externalCaseRegistrationNumber":"MBN/2019:99",\
			"decision":{"caseRegistrationNumber":"MBN/2019:123","outcome":"APPROVAL","decisionDate":"2019-04-30","executionDate":"2019-05-02",\
			"delegationReference":"MBN DEL/2019 §45"}}""");
	}

	@Test
	void aReferenceOnlyOnThePermitIsTheDelegationReferenceOfTheDecision() {
		final var contents = new AssetContents(AssetSchema.TOBACCO).caseRegistrationNumber(WITH_DELEGATION).decision(decision(null), ISSUED, null);

		assertThat(json(contents)).isEqualTo("""
			{"caseRegistrationNumber":"MBN/2019:123","externalCaseRegistrationNumber":"MBN/2019:123",\
			"decision":{"outcome":"APPROVAL","decisionDate":"2019-04-30","executionDate":"2019-05-02","delegationReference":"MBN DEL/2019 §45"}}""");
	}

	@Test
	void anEmailAddressThatTheSchemaRefusesIsLeftOut() {
		// Addresses that the format email of the json-schema service refuses, and one with å it takes but the check does not
		for (final var refused : List.of("info@firma.se,", "a..b@firma.se", ".a@firma.se", "info@-firma.se", "info@firma.s", "x@y.z1", "info;kalle@firma.se", "info@firma.com/",
			"åsa@firma.se")) {
			final var contents = new AssetContents(AssetSchema.TOBACCO).place(new Place("Butik", null, null, null, null, refused, null, null));

			assertThat(contents.fields().toNode().get("pointOfSale").has("emailAddress")).as(refused).isFalse();
			assertThat(contents.asset(null, null, null, null, List.of()).omitted()).as(refused).containsExactly("pointOfSale.emailAddress: har inte formen av en e-postadress");
		}
		for (final var taken : List.of("info@firma.se", "Info.Namn+tag@sub.firma.se", "a-b@firma-x.se", "info@firma.co.uk", "info@xn--rksmrgs-5wao1o.se")) {
			final var contents = new AssetContents(AssetSchema.TOBACCO).place(new Place("Butik", null, null, null, null, taken, null, null));

			assertThat(contents.fields().toNode().get("pointOfSale").get("emailAddress").asString()).isEqualTo(taken);
		}
	}

	@Test
	void theRestaurantNumberOfThePlaceIsWrittenByTheAlcoholSchemaOnly() {
		final var place = new Place("Krogen", null, null, null, null, null, null, "12345678");

		assertThat(json(new AssetContents(AssetSchema.ALCOHOL).place(place))).isEqualTo("""
			{"premises":{"name":"Krogen","restaurantNumber":"12345678"}}""");
		assertThat(json(new AssetContents(AssetSchema.TOBACCO).place(place))).isEqualTo("""
			{"pointOfSale":{"name":"Krogen"}}""");
	}

	@Test
	void aNotificationHasNoDecisionForTheReferenceAndKeepsTheNumberAsItIs() {
		final var contents = new AssetContents(AssetSchema.ALCOHOL).caseRegistrationNumber(WITH_DELEGATION).decision(null, ISSUED, null);

		assertThat(json(contents)).isEqualTo("""
			{"caseRegistrationNumber":"MBN/2019:123 MBN DEL/2019 §45","externalCaseRegistrationNumber":"MBN/2019:123 MBN DEL/2019 §45"}""");
	}
}
