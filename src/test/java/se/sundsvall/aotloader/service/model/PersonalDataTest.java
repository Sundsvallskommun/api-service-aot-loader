package se.sundsvall.aotloader.service.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.source.CodeTexts;
import se.sundsvall.aotloader.source.ContactRow;
import se.sundsvall.aotloader.source.InspectionVisitRows;
import se.sundsvall.aotloader.source.InspectionVisitRows.RemarkRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.RoundRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitFileRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitRow;
import se.sundsvall.aotloader.source.Journal;
import se.sundsvall.aotloader.source.PersonRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.aotloader.service.model.PartyType.PRIVATE;

/**
 * Whatever holds names or numbers of persons and companies never shows them when it is written to a log (K6).
 */
class PersonalDataTest {

	private static final String NUMBER = "191212121212";
	private static final String NAME = "Tolvan Tolvansson";

	@Test
	void theModelHidesPersonalData() {
		final var legalId = new LegalId(NUMBER, PRIVATE);

		assertHidden(legalId);
		assertHidden(new Holder(legalId, NAME, "Gatan 1", "85230", "Sundsvall", "tolvan@example.com", "070-1", NAME));
		assertHidden(new Person(NAME, legalId, false, List.of("VD")));
		assertHidden(new PermitLink(new PlacePermit(new ItemKey(Source.ALKT, Kind.TILLSTAND, "1"), null, null, true), legalId, legalId, null));
		assertHidden(Candidate.builder(new ItemKey(Source.ALKT, Kind.TILLSTAND, "1"), ErrandType.SERVING_PERMIT).holder(new Holder(legalId, NAME, null, null, null, null, null, null)).build());
		assertHidden(new AssetData(null, "Tillstånd", null, null, Map.of("permitHolderName", NAME, "permitHolderOrgNumber", NUMBER)));
		assertHidden(new InvestigationData("Tillsyn", null, null, new InvestigationTexts(NAME, NAME + " " + NUMBER, NUMBER)));
		assertHidden(new Journal.Entry(null, "Anteckning", NAME + " " + NUMBER));
	}

	@Test
	void theTextsOfTheSourcesHidePersonalData() {
		assertHidden(new AlktSnapshot.ErrandRow(1, 1, "07", null, null, null, null, List.of(NAME + " " + NUMBER)));
		assertHidden(new AlktSnapshot.DecisionRow(1, 1, "VT", null, null, null, null, null, NAME + " " + NUMBER));
		assertHidden(new AlktSnapshot.TextRow(null, "ANT", false, NAME + " " + NUMBER, true));
		assertHidden(new AlkolSnapshot.ErrandRow(1, 1, "IT", null, null, null, null, NAME + " " + NUMBER));
		assertHidden(new AlkolSnapshot.DecisionRow(1, 1, "VT", null, null, null, null, null, NAME + " " + NUMBER));
		assertHidden(new AlkolSnapshot.EventRow(1, 1, 1, "024", null, null, null, null, null, NAME + " " + NUMBER));
		assertHidden(new MeasureData("Servering till minderårig", NAME + " " + NUMBER, null));
	}

	@Test
	void theRowsOfTheSourcesHidePersonalData() {
		assertHidden(new ContactRow(1, NUMBER, NAME, NAME, null, null, null, null, null, null));
		assertHidden(new AlktSnapshot(List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), InspectionVisitRows.empty()));
		assertHidden(new PersonRow(1, NAME, NUMBER, List.of(), false));
		assertHidden(new AlkolSnapshot.SalesPlaceRow(1, NAME, null, null, null, null, null, null, null, NUMBER, null, false, false, false, false, false, 1));
		assertHidden(new AlkolSnapshot(List.of(), Map.of(), Map.of(), List.of(), List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), InspectionVisitRows.empty()));
		final var visit = new VisitRow(1, 1, null, null, null, null, NAME + " " + NUMBER, null, null, List.of(NAME), null);
		final var remark = new RemarkRow(1, "05", NAME + " " + NUMBER, null);
		final var round = new RoundRow(1, NAME, null, null, NAME + " " + NUMBER);
		assertHidden(visit);
		assertHidden(remark);
		assertHidden(round);
		assertHidden(new InspectionVisitRows(List.of(visit), Map.of(1, List.of(remark)), Map.of(), Map.of(), Map.of(), Map.of("TT", NAME), Map.of(1, round)));
	}

	@Test
	void snapshots() {
		final var alkt = new AlktSnapshot(List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of("A", Map.of("07", "Tillsyn")), InspectionVisitRows.empty());
		assertThat(alkt.codeText("A", "07")).isEqualTo("Tillsyn");
		assertThat(alkt.codeText("A", "08")).isEqualTo("08");
		assertThat(alkt.codeText("X", "08")).isEqualTo("08");
		assertThat(alkt.codeText("A", null)).isNull();
		assertThat(alkt.conditionsOf(1)).isEmpty();
		assertThat(alkt.filesOf(1)).isEmpty();
		assertThat(alkt.personsOf(1)).isEmpty();

		final var visits = new InspectionVisitRows(List.of(), Map.of(), InspectionVisitRows.filesByVisit(List.of(new VisitFileRow(1, "a.pdf"), new VisitFileRow(null, "b.pdf"), new VisitFileRow(2, null),
			new VisitFileRow(1, "c.pdf"))), Map.of("04", "Rutintillsyn"), Map.of());
		assertThat(visits.filesOf(1)).containsExactly("a.pdf", "c.pdf");
		assertThat(visits.filesOf(2)).isEmpty();
		assertThat(visits.remarksOf(1)).isEmpty();
		assertThat(visits.kindText("04")).isEqualTo("Rutintillsyn");
		assertThat(visits.remarkText("05")).isEqualTo("05");

		assertThat(CodeTexts.textOf(Map.of("KIO", "Kiosk"), "KIO")).isEqualTo("Kiosk");
		assertThat(CodeTexts.textOf(Map.of(), "KIO")).isEqualTo("KIO");
		assertThat(CodeTexts.textOf(Map.of(), null)).isNull();
		assertThat(new AlkolSnapshot.DecisionRow(1, 1, "GTDE", LocalDateTime.of(2020, 1, 2, 3, 4), null, null, null, null).beslutsDatum()).isEqualTo(LocalDate.of(2020, 1, 2));
		assertThat(new AlkolSnapshot.DecisionRow(1, 1, "GTDE", null, null, null, null, null).beslutsDatum()).isNull();
	}

	private static void assertHidden(final Object object) {
		assertThat(object.toString()).doesNotContain(NUMBER, "Tolvan", "tolvan@example.com", "Gatan");
	}
}
