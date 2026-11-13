package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import se.sundsvall.aotloader.service.TestData;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.InvestigationData;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.service.model.InvestigationTexts.Part;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.MeasureData;
import se.sundsvall.aotloader.service.model.PartyType;
import se.sundsvall.aotloader.service.model.PlacePermit;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.source.InspectionVisitRows.RemarkRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.RoundRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.StaffMarks;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitRow;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionVisitCandidatesTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "-", textBlock = """
		2023-11-04 | -          | 21:30    | 23:15    | 2023-11-04T21:30 | 2023-11-04T23:15
		2023-11-04 | -          | 9.15     | 10:00:00 | 2023-11-04T09:15 | 2023-11-04T10:00
		2023-11-04 | -          | 22:00    | 01:30    | 2023-11-04T22:00 | 2023-11-05T01:30
		2023-11-04 | 2023-11-04 | 22:00    | 01:30    | 2023-11-04T22:00 | 2023-11-05T01:30
		2023-11-04 | 2023-11-05 | 22:00    | 01:30    | 2023-11-04T22:00 | 2023-11-05T01:30
		2023-11-04 | 2023-11-06 | 22:00    | 01:30    | 2023-11-04T22:00 | 2023-11-06T01:30
		2023-11-04 | 2023-11-03 | 14:00    | 15:00    | 2023-11-04T14:00 | 2023-11-04T15:00
		2023-11-04 | -          | 22:00    | 24:00    | 2023-11-04T22:00 | 2023-11-05T00:00
		2023-11-04 | -          | 14:00:00 | -        | 2023-11-04T14:00 | 2023-11-04T14:00
		2023-11-04 | 2023-11-05 | 14:00    | -        | 2023-11-04T14:00 | 2023-11-05T00:00
		2023-11-04 | -          | -        | 10:00    | 2023-11-04T00:00 | 2023-11-04T10:00
		2023-11-04 | -          | kväll    | sent     | 2023-11-04T00:00 | 2023-11-04T00:00
		-          | 2023-11-05 | 21:30    | 23:15    | -                | -
		""")
	void aVisitStartsOnItsDateAtItsStartTimeAndEndsAtItsEndTimeOnItsEndDayOrWhenItStarted(final LocalDate date, final LocalDate endDay, final String start, final String end,
		final LocalDateTime startedAt, final LocalDateTime completedAt) {
		final var visit = new VisitRow(900, 1, date, endDay, start, end, "04", null, null, null, List.of(), StaffMarks.NONE, null, List.of(), false);

		assertThat(InspectionVisitCandidates.investigation("Tillsynsbesök", visit, List.of(), null)).isEqualTo(new InvestigationData("Tillsynsbesök", startedAt, completedAt, InvestigationTexts.NONE));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "-", textBlock = """
		21:30    | 23:15    | 21:30–23:15
		9.15     | 10:00:00 | 09:15–10:00
		14:00    | -        | 14:00
		-        | 10:00    | –10:00
		kväll    | sent     | kväll–sent
		00:00    | 01:30    | 00:00–01:30
		23:00    | 00:00    | 23:00–00:00
		00:00    | 00:00    | -
		00:00:00 | 00:00:00 | -
		0.00     | -        | -
		-        | 00:00    | -
		-        | -        | -
		""")
	void theTimeOfAVisitIsItsStartAndEndAndOnlyMidnightTellsNoTime(final String start, final String end, final String expected) {
		final var visit = VisitTestRows.visit(900, 1, LocalDate.of(2023, 11, 4), start, end, "04", null, null, null);

		assertThat(InspectionVisitCandidates.time(visit)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "-", textBlock = """
		Krogrunda | 2023-11-04 | 2023-11-05 | Krogrunda, 2023-11-04 – 2023-11-05
		Krogrunda | 2023-11-04 | 2023-11-04 | Krogrunda, 2023-11-04
		Krogrunda | 2023-11-04 | -          | Krogrunda, 2023-11-04
		Krogrunda | -          | 2023-11-05 | Krogrunda, 2023-11-05
		Krogrunda | -          | -          | Krogrunda
		-         | 2023-11-04 | 2023-11-05 | 2023-11-04 – 2023-11-05
		' '       | -          | -          | -
		""")
	void aRoundIsNamedWithItsDays(final String name, final LocalDate start, final LocalDate end, final String expected) {
		assertThat(InspectionVisitCandidates.roundName(new RoundRow(40, name, start, end, null))).isEqualTo(expected);
	}

	@Test
	void theRemarksOfAVisitAreItsMeasures() {
		final var rows = VisitTestRows.visits(List.of(), Map.of(), Map.of(), Map.of(), Map.of("05", "Servering till minderårig"));
		final var remarks = List.of(
			new RemarkRow(900, "05", " Ungdomar i baren ", null),
			new RemarkRow(900, "77", null, null),
			new RemarkRow(900, null, "Bara en notering", null),
			new RemarkRow(900, null, " ", "bild.jpg"));

		final var visited = LocalDateTime.of(2023, 11, 4, 23, 0);

		// A remark without a code is headed "Anmärkning", and one with neither a code nor a note is no measure
		assertThat(InspectionVisitCandidates.measures(remarks, rows, visited)).containsExactly(
			new MeasureData("Servering till minderårig", "Ungdomar i baren", visited),
			new MeasureData("77", null, visited),
			new MeasureData("Anmärkning", "Bara en notering", visited));
	}

	@Test
	void theTextsOfTheInvestigationOfAVisitAreItsRoundItsNoteAndItsRemarks() {
		final var visit = VisitTestRows.visit(900, 1, LocalDate.of(2023, 11, 4), null, null, "04", " Lugnt i baren. ", null, null);

		assertThat(InspectionVisitCandidates.investigation("Tillsynsbesök", visit, List.of("Anmärkning"), new RoundRow(40, "Krogrunda", null, null, "Med polisen.")).texts())
			.isEqualTo(InvestigationTexts.of(new Part("Tillsynsomgång", "Krogrunda\nMed polisen."), new Part("Anteckning", "Lugnt i baren."), new Part("Anmärkningar", "Anmärkning")));
		// A round without a name, days or a note tells nothing
		assertThat(InspectionVisitCandidates.investigation("Tillsynsbesök", visit, List.of(), new RoundRow(41, null, null, null, " ")).texts())
			.isEqualTo(InvestigationTexts.of(new Part("Anteckning", "Lugnt i baren.")));
		assertThat(InspectionVisitCandidates.roundName(null)).isNull();
	}

	@Test
	void whoMadeTheInspectionIsTheKontrollantAndAVisitWithoutRemarksSaysSo() {
		final var visit = VisitTestRows.visit(900, 1, LocalDate.of(2023, 11, 4), null, null, "04", null, null, null, List.of("POL"), new StaffMarks(true, false, false, false));
		final var rows = VisitTestRows.visits(List.of(visit), Map.of(), Map.of(), Map.of("04", "Rutintillsyn"), Map.of(), Map.of("POL", "Polisen"));
		final var visits = new InspectionVisitCandidates(ErrandType.ALCOHOL_INSPECTION_VISIT, TestData.properties(), "okänt serveringsställe", "tillsynsbesöket saknar serveringsställe",
			LocalDate.of(2026, 10, 23));

		final var candidate = visits.builder(visit, rows, null, null, null).build();

		// As in AlkT, which may name the police, the rescue service or the environment office as well as a handler (decisions
		// 66 and 67)
		assertThat(candidate.errand().description()).isEqualTo("""
			Tillsynsbesök på okänt serveringsställe 2023-11-04.
			Tillsynsart: Rutintillsyn
			Kontrollant: Polisen
			Anmärkningar: Inga anmärkningar registrerade
			Serveringspersonal: Ägaren på plats""");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"2020-01-01", "2023-11-04", "2024-12-31"
	})
	void aVisitMadeWhileThePermitHeldConcernsTheCurrentOwner(final LocalDate date) {
		final var holder = holderOf(date, permit(true), owner());

		assertThat(holder).isNotNull();
		assertThat(holder.legalId()).isEqualTo(new LegalId("5566778899", PartyType.ENTERPRISE));
		assertThat(holder.name()).isEqualTo("Krogen AB");
		assertThat(holder.contactPerson()).isEqualTo("Kim Krögare");
	}

	@ParameterizedTest
	@CsvSource(nullValues = "-", value = {
		"2019-12-31, true", "2025-01-01, true", "-, true", "2023-11-04, false"
	})
	void aVisitOfAnotherTimeOrUnderAPermitThatMayBeAnothersConcernsNoKnownHolder(final LocalDate date, final boolean issuedToOwner) {
		assertThat(holderOf(date, permit(issuedToOwner), owner())).isNull();
	}

	@Test
	void aVisitWithoutADateIsMadeOnTheDayOfItsRoundOfOneDay() {
		final var visit = new VisitRow(900, 1, null, null, "21:30", "23:00", "04", null, null, null, List.of(), StaffMarks.NONE, 7, List.of(), false);
		final var rows = new InspectionVisitRows(List.of(visit), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
			Map.of(7, new RoundRow(7, "Krogrunda", LocalDate.of(2023, 11, 4), LocalDate.of(2023, 11, 4), null)));

		final var candidate = new InspectionVisitCandidates(ErrandType.ALCOHOL_INSPECTION_VISIT, TestData.properties(), "okänt serveringsställe", "tillsynsbesöket saknar serveringsställe",
			LocalDate.of(2026, 10, 23)).builder(visit, rows, null, permit(true), owner()).build();

		// The day of the round is the day of the visit, which it is migrated with and related to the permit by (decision 121)
		assertThat(candidate.ruleHits()).extracting(RuleHit::detail).containsExactly("tillsynsbesöket saknar serveringsställe", "datum saknas, dagen 2023-11-04 är härledd ur tillsynsomgången");
		assertThat(candidate.errand().description()).startsWith("""
			Tillsynsbesök på okänt serveringsställe 2023-11-04.
			Datum: saknas i källsystemet, dagen är härledd ur tillsynsomgången
			""");
		assertThat(candidate.investigation().startedAt()).isEqualTo(LocalDateTime.of(2023, 11, 4, 21, 30));
		assertThat(candidate.investigation().completedAt()).isEqualTo(LocalDateTime.of(2023, 11, 4, 23, 0));
		assertThat(candidate.holder()).isNotNull();
	}

	@Test
	void aVisitToAPlaceWithoutPermitOrOwnerConcernsNoKnownHolder() {
		final var date = LocalDate.of(2023, 11, 4);

		assertThat(holderOf(date, null, owner())).isNull();
		assertThat(holderOf(date, permit(true), null)).isNull();
	}

	/**
	 * @return the holder of a visit on the day to a place with the permit and the owner
	 */
	private static Holder holderOf(final LocalDate date, final PlacePermit permit, final ContactRow owner) {
		final var visit = VisitTestRows.visit(900, 1, date, null, null, "04", null, null, null);
		final var rows = VisitTestRows.visits(List.of(visit), Map.of(), Map.of(), Map.of(), Map.of());
		return new InspectionVisitCandidates(ErrandType.ALCOHOL_INSPECTION_VISIT, TestData.properties(), "okänt serveringsställe", "tillsynsbesöket saknar serveringsställe",
			LocalDate.of(2026, 10, 23)).builder(visit, rows, null, permit, owner).build().holder();
	}

	/**
	 * @return the permit of the place from 2020 through 2024
	 */
	private static PlacePermit permit(final boolean issuedToOwner) {
		return new PlacePermit(new ItemKey(Source.ALKT, Kind.TILLSTAND, "1"), LocalDate.of(2020, 1, 1), LocalDate.of(2024, 12, 31), issuedToOwner);
	}

	private static ContactRow owner() {
		return new ContactRow(10, "556677-8899", "Krogen AB", "Kim Krögare", "Gatan 1", null, "852 30", "Sundsvall", "060-1", "krogen@example.com");
	}

	@Test
	void theContentOfAVisitShowsNoNamesOrRemarks() {
		final var visit = VisitTestRows.visit(900, 1, LocalDate.of(2023, 11, 4), null, null, "04", " Lugnt i baren. ", null, null);
		final var content = new InspectionVisitCandidates.VisitContent(visit, null, "Tillsynsbesök", "14:05", List.of("Anmärkning"), List.of("Tolvan Tolvansson"), List.of("Saknade utbildning"),
			null);

		assertThat(content).hasToString("VisitContent[visit=VisitRow[tillfalleNr=900], kind=Tillsynsbesök]");
	}

	@Test
	void aVisitWithoutADateGetsTheDayTheSourceTellsOtherwise() {
		final var today = LocalDate.of(2026, 11, 13);
		final var oneDay = new RoundRow(1, "Krogrunda", LocalDate.of(2023, 11, 4), LocalDate.of(2023, 11, 4), null);
		final var twoDays = new RoundRow(2, "Krogrunda", LocalDate.of(2023, 11, 4), LocalDate.of(2023, 11, 5), null);
		final var dated = VisitTestRows.visit(900, 1, LocalDate.of(2023, 11, 3), null, null, "04", null, null, null);
		final var undated = VisitTestRows.visit(901, 1, null, null, null, "04", null, null, null);
		final var withEndDay = new VisitRow(902, 1, null, LocalDate.of(2023, 11, 6), null, null, "04", null, null, null, List.of(), StaffMarks.NONE, 1, List.of(), false);
		final var endDayLater = new VisitRow(903, 1, null, today.plusDays(1), null, null, "04", null, null, null, List.of(), StaffMarks.NONE, 1, List.of(), false);

		// A visit with a date keeps it, also with a round
		assertThat(InspectionVisitCandidates.VisitDay.of(dated, oneDay, today)).isEqualTo(new InspectionVisitCandidates.VisitDay(LocalDate.of(2023, 11, 3), null));
		assertThat(InspectionVisitCandidates.VisitDay.of(dated, oneDay, today).description()).isNull();
		// Its end day goes before its round, and a round of one day tells the day, but not one of several
		assertThat(InspectionVisitCandidates.VisitDay.of(withEndDay, oneDay, today)).isEqualTo(new InspectionVisitCandidates.VisitDay(LocalDate.of(2023, 11, 6), "besökets slutdatum"));
		assertThat(InspectionVisitCandidates.VisitDay.of(undated, oneDay, today).description()).isEqualTo("saknas i källsystemet, dagen är härledd ur tillsynsomgången");
		assertThat(InspectionVisitCandidates.VisitDay.of(undated, oneDay, today).date()).isEqualTo(LocalDate.of(2023, 11, 4));
		assertThat(InspectionVisitCandidates.VisitDay.of(undated, twoDays, today)).isEqualTo(new InspectionVisitCandidates.VisitDay(null, null));
		assertThat(InspectionVisitCandidates.VisitDay.of(undated, null, today).description()).isEqualTo("saknas i källsystemet");
		// A day after the migration is no day the visit was made
		assertThat(InspectionVisitCandidates.VisitDay.of(endDayLater, oneDay, today)).isEqualTo(new InspectionVisitCandidates.VisitDay(LocalDate.of(2023, 11, 4), "tillsynsomgången"));
		assertThat(InspectionVisitCandidates.VisitDay.of(endDayLater, null, today).date()).isNull();
	}
}
