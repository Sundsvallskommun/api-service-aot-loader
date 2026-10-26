package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.FileAreaDirectories;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.source.PermitDecisions.PermitDays;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.files.SharedFileNames;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;

class PermitDecisionsTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
	private static final SharedFileNames NONE_SHARED = SharedFileNames.builder(new FileAreaDirectories("//server/alkt/Dokument", "//server/alkt/Bilder")).build();

	@Test
	void theDecisionHasWhatTheSourceSaysAndLinksItsDocumentAndAttachment() {
		final var files = new FileList(NONE_SHARED).add("beslut.rtf", DOCUMENTS).add("signerat.pdf", ATTACHMENTS).add("ansokan.pdf", ATTACHMENTS);
		final var decision = new DecisionRow(100, 500, "GT", LocalDateTime.of(2019, 4, 30, 9, 15), LocalDate.of(2019, 5, 1), LocalDate.of(2024, 4, 30), "TDB", "beslut.rtf", "signerat.pdf",
			"Tillstånd beviljas.", "ABE");

		final var data = PermitDecisions.of(decision, " Stadigvarande tillstånd ", "Anna Berg", days(LocalDate.of(2019, 4, 1), LocalDate.of(2030, 1, 1)), List.of("Mat ska serveras"), files, Source.ALKT);

		assertThat(data.decidedBy()).isEqualTo("Anna Berg");
		assertThat(data.decidedAt()).isEqualTo(LocalDateTime.of(2019, 4, 30, 9, 15));
		assertThat(data.description()).isEqualTo("Stadigvarande tillstånd");
		assertThat(data.justification()).isEqualTo("Tillstånd beviljas.");
		// The decision's own days come before those of the permit
		assertThat(data.validFrom()).isEqualTo(LocalDate.of(2019, 5, 1));
		assertThat(data.validTo()).isEqualTo(LocalDate.of(2024, 4, 30));
		assertThat(data.terms()).containsExactly("Mat ska serveras");
		assertThat(data.documents()).containsExactly(new FileReference("beslut.rtf", DOCUMENTS), new FileReference("signerat.pdf", ATTACHMENTS));
		assertThat(data).hasToString("DecisionData[decidedAt=2019-04-30T09:15, terms=1, documents=2, decided=0]");
	}

	@Test
	void whatTheDecisionLacksThePermitGives() {
		// The document is kept on the errand only, as when it may be a former holder's, and the attachment is not among the
		// files
		final var files = new FileList(NONE_SHARED).addToErrandOnly("beslut.rtf", DOCUMENTS);
		final var decision = new DecisionRow(100, 500, "GT", null, null, null, null, "beslut.rtf", "saknas.pdf", null, null);

		final var data = PermitDecisions.of(decision, null, " ", days(LocalDate.of(2019, 4, 1), LocalDate.of(2030, 1, 1)), List.of(), files, Source.ALKOL);

		assertThat(data.decidedBy()).isEqualTo("Uppgift saknas i AlkOL");
		assertThat(data.decidedAt()).isEqualTo(LocalDateTime.of(2019, 4, 1, 0, 0));
		assertThat(data.description()).isNull();
		assertThat(data.justification()).isNull();
		assertThat(data.validFrom()).isEqualTo(LocalDate.of(2019, 4, 1));
		assertThat(data.validTo()).isEqualTo(LocalDate.of(2030, 1, 1));
		assertThat(data.terms()).isEmpty();
		assertThat(data.documents()).isEmpty();
	}

	@Test
	void aPermitWithoutADecisionThatGrantedItGetsOneBuiltFromThePermit() {
		final var data = PermitDecisions.fromPermit(days(LocalDate.of(2019, 4, 1), LocalDate.of(2030, 1, 1)), List.of("Mat ska serveras"), Source.ALKT);

		assertThat(data.decidedBy()).isEqualTo("Uppgift saknas i AlkT");
		assertThat(data.decidedAt()).isEqualTo(LocalDateTime.of(2019, 4, 1, 0, 0));
		assertThat(data.description()).isEqualTo("Beslutet finns inte i AlkT och bygger på tillståndet");
		assertThat(data.justification()).isNull();
		assertThat(data.validFrom()).isEqualTo(LocalDate.of(2019, 4, 1));
		assertThat(data.validTo()).isEqualTo(LocalDate.of(2030, 1, 1));
		assertThat(data.terms()).containsExactly("Mat ska serveras");
		assertThat(data.documents()).isEmpty();
		// Without the day it was issued it gets none
		assertThat(PermitDecisions.fromPermit(days(null, null), List.of(), Source.ALKT)).isNull();
	}

	@Test
	void aPermitWithoutADecisionOrATimeForItGetsNone() {
		final var files = new FileList(NONE_SHARED);
		final var undated = new DecisionRow(100, 500, "GT", null, null, null, null, null, null, null, "ABE");

		assertThat(PermitDecisions.of(null, "Stadigvarande tillstånd", "Anna Berg", days(LocalDate.of(2019, 4, 1), null), List.of(), files, Source.ALKT)).isNull();
		assertThat(PermitDecisions.of(undated, "Stadigvarande tillstånd", "Anna Berg", days(null, null), List.of(), files, Source.ALKT)).isNull();
	}

	@Test
	void aDecisionWithoutATimeIsNeverMadeAfterTheMigration() {
		final var files = new FileList(NONE_SHARED);
		final var undated = new DecisionRow(100, 500, "GTDE", null, LocalDate.of(2026, 11, 1), null, null, null, null, null, null);
		// Valid from a day after the migration: the day its errand was closed stands in
		final var later = PermitDays.of(LocalDate.of(2026, 11, 1), null, LocalDate.of(2026, 9, 15), TODAY);

		final var data = PermitDecisions.of(undated, null, null, later, List.of(), files, Source.ALKOL);

		assertThat(data.decidedAt()).isEqualTo(LocalDateTime.of(2026, 9, 15, 0, 0));
		assertThat(data.validFrom()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(PermitDays.of(LocalDate.of(2026, 10, 26), null, LocalDate.of(2026, 9, 15), TODAY).decidedOn()).isEqualTo(LocalDate.of(2026, 10, 26));
		// Without a day on or before the migration, the decision has none, and the errand gets no decision
		final var noDay = PermitDays.of(LocalDate.of(2026, 11, 1), null, null, TODAY);
		assertThat(noDay.decidedOn()).isNull();
		assertThat(PermitDecisions.of(undated, null, null, noDay, List.of(), files, Source.ALKOL)).isNull();
		assertThat(PermitDecisions.fromPermit(noDay, List.of(), Source.ALKT)).isNull();
		assertThat(PermitDays.of(LocalDate.of(2026, 11, 1), null, LocalDate.of(2026, 11, 2), TODAY).decidedOn()).isNull();
	}

	private static PermitDays days(final LocalDate issued, final LocalDate validTo) {
		return PermitDays.of(issued, validTo, null, TODAY);
	}
}
