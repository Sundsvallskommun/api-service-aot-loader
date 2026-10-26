package se.sundsvall.aotloader.source.alkt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.service.model.ServingPermitSubtype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static se.sundsvall.aotloader.service.model.ReviewRule.SERVING_TIME_UNPLACED;
import static se.sundsvall.aotloader.service.model.ServingPermitSubtype.FARM_SALES;
import static se.sundsvall.aotloader.service.model.ServingPermitSubtype.PERMANENT_SERVING;
import static se.sundsvall.aotloader.service.model.ServingPermitSubtype.TEMPORARY_SERVING_PUBLIC;
import static se.sundsvall.aotloader.source.alkt.AlktTestData.permit;

class DecidedScopesTest {

	@Test
	void eachTimeGoesWhereItsTextSaysForWhomThePermitServes() {
		final var hits = new ArrayList<RuleHit>();
		final var row = permit(1).noTimes().servingTo(true, true).time("11:00", "01:00", "Inomhus").time("11:00", "22:00", "Uteservering").build();

		final var parameters = DecidedScopes.of(row, PERMANENT_SERVING, hits);

		assertThat(parameters).extracting(Parameter::key, Parameter::displayName, Parameter::group, Parameter::values).containsExactly(
			tuple("decidedBeverages", "Alkoholdrycker som får serveras", "DECIDED", List.of("STARKOL", "VIN")),
			tuple("decidedServingHoursIndoorPublic", "Serveringstid inne (allmänheten)", "DECIDED", List.of("11:00", "01:00")),
			tuple("decidedServingHoursOutdoorPublic", "Serveringstid ute (allmänheten)", "DECIDED", List.of("11:00", "22:00")),
			tuple("decidedServingHoursIndoorPrivate", "Serveringstid inne (slutet sällskap)", "DECIDED", List.of("11:00", "01:00")),
			tuple("decidedServingHoursOutdoorPrivate", "Serveringstid ute (slutet sällskap)", "DECIDED", List.of("11:00", "22:00")));
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

		final var hours = hours(DecidedScopes.of(row, PERMANENT_SERVING, hits));

		assertThat(hours).containsOnly(
			entry("decidedServingHoursIndoorPublic", List.of("11:00", "01:00")),
			entry("decidedServingHoursIndoorPrivate", List.of("14:00", "01:00")),
			entry("decidedServingHoursOutdoorPublic", List.of("11:00", "22:00")));
		assertThat(hits).isEmpty();
	}

	@Test
	void theFirstTimeWithoutAPlaceIsIndoorsAndOneThatSaysBothGoesBoth() {
		final var hits = new ArrayList<RuleHit>();
		final var row = permit(1).noTimes().servingTo(true, false).scope(true, false, false, false).time("11:00", "24:00", null).build();
		final var both = permit(2).noTimes().servingTo(true, false).time("12:00", "23:00", "Inomhus och uteservering").build();

		assertThat(hours(DecidedScopes.of(row, PERMANENT_SERVING, hits))).containsOnly(entry("decidedServingHoursIndoorPublic", List.of("11:00", "00:00")));
		assertThat(hours(DecidedScopes.of(both, PERMANENT_SERVING, hits))).containsOnly(
			entry("decidedServingHoursIndoorPublic", List.of("12:00", "23:00")),
			entry("decidedServingHoursOutdoorPublic", List.of("12:00", "23:00")));
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

		final var hours = hours(DecidedScopes.of(row, PERMANENT_SERVING, hits));

		// The first time is indoors, as is the third, and they differ
		assertThat(hours).isEmpty();
		assertThat(hits).extracting(RuleHit::rule, RuleHit::detail).containsExactly(tuple(SERVING_TIME_UNPLACED,
			"serveringstid 2 (11:00–03:00): texten säger inte inne eller ute; Serveringstid inne (allmänheten): serveringstid 1 (11:00–01:00) och serveringstid 3 (11:00–02:00) har olika tider"));
	}

	@Test
	void aPermitThatServesNobodyGetsNoTime() {
		final var hits = new ArrayList<RuleHit>();
		final var row = permit(1).noTimes().servingTo(false, false).time("11:00", "01:00", "Inomhus").build();

		assertThat(hours(DecidedScopes.of(row, PERMANENT_SERVING, hits))).isEmpty();
		assertThat(hits).extracting(RuleHit::detail).containsExactly("serveringstid 1 (11:00–01:00): tillståndet anger inte om serveringen är för allmänheten eller slutet sällskap");
	}

	@Test
	void aTemporaryPermitAndFarmSalesGetTheirOneTime() {
		final var hits = new ArrayList<RuleHit>();
		final var one = permit(1).noTimes().time("10:00", "18:00", "Gården").build();
		final var two = permit(2).noTimes().time("10:00", "18:00", null).time("10:00", "20:00", null).build();

		assertThat(hours(DecidedScopes.of(one, FARM_SALES, hits))).containsOnly(entry("decidedServingHours", List.of("10:00", "18:00")));
		assertThat(hours(DecidedScopes.of(one, TEMPORARY_SERVING_PUBLIC, hits))).containsOnly(entry("decidedServingHours", List.of("10:00", "18:00")));
		assertThat(hits).isEmpty();
		assertThat(hours(DecidedScopes.of(two, TEMPORARY_SERVING_PUBLIC, hits))).isEmpty();
		assertThat(hits).extracting(RuleHit::detail).containsExactly("tillståndet har 2 olika serveringstider, och Draken har ett fält för den här sortens tillstånd");
	}

	@ParameterizedTest
	@EnumSource(value = ServingPermitSubtype.class, names = {
		"PERMANENT_CATERING", "TASTING"
	})
	void aSubtypeDrakenHasNoFieldOfServingHoursForGetsOnlyItsBeverages(final ServingPermitSubtype subtype) {
		final var hits = new ArrayList<RuleHit>();
		final var row = permit(1).drinks(false, false, true, true, true).time("11:00", "01:00", "Inomhus").build();

		assertThat(DecidedScopes.of(row, subtype, hits)).extracting(Parameter::key, Parameter::values)
			.containsExactly(tuple("decidedBeverages", List.of("SPRITDRYCKER", "CIDER_ELLER_ANDRA_JASTA_ALKOHOLDRYCKER")));
		assertThat(DecidedScopes.of(row, null, hits)).extracting(Parameter::key).containsExactly("decidedBeverages");
		assertThat(hits).isEmpty();
	}

	@Test
	void aPermitWithoutTheBeveragesDrakenKnowsGetsNone() {
		final var row = permit(1).drinks(false, false, false, false, true).noTimes().build();

		assertThat(DecidedScopes.of(row, PERMANENT_SERVING, new ArrayList<>())).isEmpty();
	}

	@Test
	void aTimeThatCannotBeReadIsLeftOutHere() {
		final var hits = new ArrayList<RuleHit>();
		final var row = permit(1).noTimes().time("11:00", "sent", "Inomhus").time("11:00", "22:00", "Uteservering").build();

		assertThat(hours(DecidedScopes.of(row, PERMANENT_SERVING, hits))).containsOnly(entry("decidedServingHoursOutdoorPublic", List.of("11:00", "22:00")));
		assertThat(hits).isEmpty();
	}

	private static Map<String, List<String>> hours(final List<Parameter> parameters) {
		return parameters.stream().filter(parameter -> !"decidedBeverages".equals(parameter.key())).collect(Collectors.toMap(Parameter::key, Parameter::values));
	}
}
