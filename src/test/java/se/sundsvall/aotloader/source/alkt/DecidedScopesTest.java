package se.sundsvall.aotloader.source.alkt;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.service.model.ServingPermitSubtype;
import se.sundsvall.aotloader.source.alkt.DecidedScopes.Decided;
import se.sundsvall.aotloader.support.JsonFields;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static se.sundsvall.aotloader.service.model.ReviewRule.SERVING_TIME_UNPLACED;
import static se.sundsvall.aotloader.service.model.ServingPermitSubtype.FARM_SALES;
import static se.sundsvall.aotloader.service.model.ServingPermitSubtype.PERMANENT_SERVING;
import static se.sundsvall.aotloader.service.model.ServingPermitSubtype.TEMPORARY_SERVING_PUBLIC;
import static se.sundsvall.aotloader.source.alkt.AlktTestData.permit;

class DecidedScopesTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void eachTimeGoesWhereItsTextSaysForWhomThePermitServes() {
		final var hits = new ArrayList<RuleHit>();
		final var row = permit(1).noTimes().servingTo(true, true).time("11:00", "01:00", "Inomhus").time("11:00", "22:00", "Uteservering").build();

		final var decided = DecidedScopes.of(row, PERMANENT_SERVING, hits);

		// As Draken writes them in the JSON of the decision: the fields in the order Draken shows them
		assertThat(json(decided)).isEqualTo("""
			{"beverages":["WINE","STRONG_BEER"],"servingHours":[\
			{"audience":"PUBLIC","location":"INDOOR","from":"11:00","to":"01:00"},\
			{"audience":"PUBLIC","location":"OUTDOOR","from":"11:00","to":"22:00"},\
			{"audience":"CLOSED_COMPANY","location":"INDOOR","from":"11:00","to":"01:00"},\
			{"audience":"CLOSED_COMPANY","location":"OUTDOOR","from":"11:00","to":"22:00"}]}""");
		assertThat(hits).isEmpty();
	}

	@Test
	void aTextThatNamesWhomTheTimeIsForGoesBeforeOneThatDoesNot() {
		final var hits = new ArrayList<RuleHit>();
		final var row = permit(1).noTimes().servingTo(true, true)
			.time("11:00", "01:00", "Restaurang")
			.time("14:00", "01:00", "Inomhus slutet sällskap")
			.time("11:00", "22:00", "Takterass för allmänheten")
			.build();

		assertThat(hours(DecidedScopes.of(row, PERMANENT_SERVING, hits))).isEqualTo("""
			[{"audience":"PUBLIC","location":"INDOOR","from":"11:00","to":"01:00"},\
			{"audience":"PUBLIC","location":"OUTDOOR","from":"11:00","to":"22:00"},\
			{"audience":"CLOSED_COMPANY","location":"INDOOR","from":"14:00","to":"01:00"}]""");
		assertThat(hits).isEmpty();
	}

	@Test
	void theFirstTimeWithoutAPlaceIsIndoorsAndOneThatSaysBothGoesBoth() {
		final var hits = new ArrayList<RuleHit>();
		final var row = permit(1).noTimes().servingTo(true, false).scope(true, false, false, false).time("11:00", "24:00", null).build();
		final var both = permit(2).noTimes().servingTo(true, false).time("12:00", "23:00", "Inomhus och uteservering").build();

		assertThat(hours(DecidedScopes.of(row, PERMANENT_SERVING, hits))).isEqualTo("""
			[{"audience":"PUBLIC","location":"INDOOR","from":"11:00","to":"00:00"}]""");
		assertThat(hours(DecidedScopes.of(both, PERMANENT_SERVING, hits))).isEqualTo("""
			[{"audience":"PUBLIC","location":"INDOOR","from":"12:00","to":"23:00"},{"audience":"PUBLIC","location":"OUTDOOR","from":"12:00","to":"23:00"}]""");
		assertThat(hits).isEmpty();
	}

	@Test
	void aRoomSaysIndoorsOnlyInATextThatSaysNothingOfOutdoors() {
		final var hits = new ArrayList<RuleHit>();
		final var row = permit(1).noTimes().servingTo(true, false)
			.time("11:00", "01:00", "Restauranglokal")
			.time("11:00", "22:00", "Uteservering i anslutning till restaurangen")
			.build();
		final var both = permit(2).noTimes().servingTo(true, false)
			.time("12:00", "23:00", "Restaurang inomhus och uteservering")
			.build();

		assertThat(hours(DecidedScopes.of(row, PERMANENT_SERVING, hits))).isEqualTo("""
			[{"audience":"PUBLIC","location":"INDOOR","from":"11:00","to":"01:00"},{"audience":"PUBLIC","location":"OUTDOOR","from":"11:00","to":"22:00"}]""");
		assertThat(hours(DecidedScopes.of(both, PERMANENT_SERVING, hits))).isEqualTo("""
			[{"audience":"PUBLIC","location":"INDOOR","from":"12:00","to":"23:00"},{"audience":"PUBLIC","location":"OUTDOOR","from":"12:00","to":"23:00"}]""");
		assertThat(hits).isEmpty();
	}

	@Test
	void aTimeThatCannotBePlacedAndAFieldWithTwoTimesAreLeftOut() {
		final var hits = new ArrayList<RuleHit>();
		final var row = permit(1).noTimes().servingTo(true, false)
			.time("11:00", "01:00", "måndag till torsdag")
			.time("11:00", "03:00", "fredag och lördag")
			.time("11:00", "02:00", "Inomhus")
			.build();

		final var decided = DecidedScopes.of(row, PERMANENT_SERVING, hits);

		// The first time is indoors, as is the third, and they differ
		assertThat(decided.servingHours()).isEmpty();
		assertThat(hits).extracting(RuleHit::rule, RuleHit::detail).containsExactly(tuple(SERVING_TIME_UNPLACED,
			"serveringstid 2 (11:00–03:00): texten säger inte inne eller ute; Serveringstid inne (allmänheten): serveringstid 1 (11:00–01:00) och serveringstid 3 (11:00–02:00) har olika tider"));
	}

	@Test
	void aPermitThatServesNobodyGetsNoTime() {
		final var hits = new ArrayList<RuleHit>();
		final var row = permit(1).noTimes().servingTo(false, false).time("11:00", "01:00", "Inomhus").build();

		assertThat(DecidedScopes.of(row, PERMANENT_SERVING, hits).servingHours()).isEmpty();
		assertThat(hits).extracting(RuleHit::detail).containsExactly("serveringstid 1 (11:00–01:00): tillståndet anger inte om serveringen är för allmänheten eller slutet sällskap");
	}

	@Test
	void aTemporaryPermitAndFarmSalesGetTheirOneTimeWithoutAudienceAndPlace() {
		final var hits = new ArrayList<RuleHit>();
		final var one = permit(1).noTimes().time("10:00", "18:00", "Gården").build();
		final var two = permit(2).noTimes().time("10:00", "18:00", null).time("10:00", "20:00", null).build();

		// Draken's one field is the entry without audience and place
		assertThat(hours(DecidedScopes.of(one, FARM_SALES, hits))).isEqualTo("""
			[{"from":"10:00","to":"18:00"}]""");
		assertThat(hours(DecidedScopes.of(one, TEMPORARY_SERVING_PUBLIC, hits))).isEqualTo("""
			[{"from":"10:00","to":"18:00"}]""");
		assertThat(hits).isEmpty();
		assertThat(DecidedScopes.of(two, TEMPORARY_SERVING_PUBLIC, hits).servingHours()).isEmpty();
		assertThat(hits).extracting(RuleHit::detail).containsExactly("tillståndet har 2 olika serveringstider, och Draken har ett fält för den här sortens tillstånd");
	}

	@ParameterizedTest
	@EnumSource(value = ServingPermitSubtype.class, names = {
		"PERMANENT_CATERING", "TASTING"
	})
	void aSubtypeDrakenHasNoFieldOfServingHoursForGetsOnlyItsBeverages(final ServingPermitSubtype subtype) {
		final var hits = new ArrayList<RuleHit>();
		final var row = permit(1).drinks(false, false, true, true, true).time("11:00", "01:00", "Inomhus").build();

		assertThat(json(DecidedScopes.of(row, subtype, hits))).isEqualTo("""
			{"beverages":["SPIRITS","OTHER_FERMENTED_BEVERAGES","ALCOHOLIC_BEVERAGE_LIKE_PREPARATIONS"]}""");
		assertThat(json(DecidedScopes.of(row, null, hits))).isEqualTo("""
			{"beverages":["SPIRITS","OTHER_FERMENTED_BEVERAGES","ALCOHOLIC_BEVERAGE_LIKE_PREPARATIONS"]}""");
		assertThat(hits).isEmpty();
	}

	@Test
	void alcoholLikePreparationsAreDecidedAsABeverage() {
		final var row = permit(1).drinks(false, false, false, false, true).noTimes().build();

		assertThat(DecidedScopes.of(row, PERMANENT_SERVING, new ArrayList<>()).beverages()).containsExactly("ALCOHOLIC_BEVERAGE_LIKE_PREPARATIONS");
	}

	@Test
	void aTimeThatCannotBeReadIsLeftOutHere() {
		final var hits = new ArrayList<RuleHit>();
		final var row = permit(1).noTimes().time("11:00", "sent", "Inomhus").time("11:00", "22:00", "Uteservering").build();

		assertThat(hours(DecidedScopes.of(row, PERMANENT_SERVING, hits))).isEqualTo("""
			[{"audience":"PUBLIC","location":"OUTDOOR","from":"11:00","to":"22:00"}]""");
		assertThat(hits).isEmpty();
	}

	@Test
	void theDecisionReplacesTheBeveragesAndServingHoursOfThePermitAndKeepsTheRest() {
		final var permit = JsonFields.object()
			.text("caseRegistrationNumber", "SN 2019/1")
			.texts("beverages", List.of("SPIRITS", "ALCOHOLIC_BEVERAGE_LIKE_PREPARATIONS"))
			.objects("servingHours", List.of(JsonFields.object().text("from", "11:00").text("to", "01:00").text("description", "Alla dagar")))
			.text("tasting", "kept")
			.toNode();
		final var decided = new Decided(List.of("SPIRITS"), List.of(JsonFields.object().text("audience", "PUBLIC").text("location", "INDOOR").text("from", "11:00").text("to", "01:00")));

		// In the place of those of the permit, which is not changed
		assertThat(JSON.writeValueAsString(decided.applyTo(permit))).isEqualTo("""
			{"caseRegistrationNumber":"SN 2019/1","beverages":["SPIRITS"],\
			"servingHours":[{"audience":"PUBLIC","location":"INDOOR","from":"11:00","to":"01:00"}],"tasting":"kept"}""");
		assertThat(permit.get("beverages")).hasSize(2);
		// A field the decision has nothing for is left out, so that Draken does not read the times of the permit as its own
		assertThat(JSON.writeValueAsString(new Decided(List.of(), List.of()).applyTo(permit))).isEqualTo("""
			{"caseRegistrationNumber":"SN 2019/1","tasting":"kept"}""");
		assertThat(new Decided(List.of("WINE"), List.of())).hasToString("Decided[beverages=[WINE], servingHours=0]");
	}

	@Test
	void onThePostEachTimeHasTheAudienceAndPlaceItsTextNamesAloneAndItsText() {
		final var row = permit(1).noTimes().servingTo(true, true)
			.time("11:00", "01:00", "Inomhus för slutna sällskap")
			.time("11:00", "22:00", null)
			.time("12:00", "24:00", "Inne och ute")
			.time("11:00", "sent", "Fredagar")
			.build();

		// A time without a place is not taken for indoors here, since a time without one holds for all serving
		assertThat(JSON.writeValueAsString(DecidedScopes.servingHours(row).stream().map(JsonFields::toNode).toList())).isEqualTo("""
			[{"audience":"CLOSED_COMPANY","location":"INDOOR","from":"11:00","to":"01:00","description":"Inomhus för slutna sällskap"},\
			{"from":"11:00","to":"22:00"},\
			{"from":"12:00","to":"24:00","description":"Inne och ute"},\
			{"description":"11:00–sent Fredagar"}]""");
	}

	private static String json(final Decided decided) {
		return JSON.writeValueAsString(decided.applyTo(JsonNodeFactory.instance.objectNode()));
	}

	private static String hours(final Decided decided) {
		return JSON.writeValueAsString(decided.servingHours().stream().map(JsonFields::toNode).toList());
	}
}
