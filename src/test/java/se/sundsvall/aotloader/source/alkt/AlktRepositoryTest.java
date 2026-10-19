package se.sundsvall.aotloader.source.alkt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import se.sundsvall.aotloader.service.model.FileDetails;
import se.sundsvall.aotloader.source.FilePurposes;
import se.sundsvall.aotloader.source.alkt.AlktRepository.EventFile;
import se.sundsvall.aotloader.source.alkt.AlktRepository.EventFiles;
import se.sundsvall.aotloader.source.alkt.AlktRepository.EventText;
import se.sundsvall.aotloader.source.alkt.AlktRepository.EventTexts;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.DocumentRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.FileRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.TextRow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlktRepositoryTest {

	@Test
	void theFilesOfAnEventOfCommunicationAreMaterialOfTheInvestigation() {
		final var events = List.of(new EventFile(1, 500, 1, "KOMU", "BALLM", LocalDateTime.of(2020, 1, 1, 9, 0), "11111111.rtf", "svar.pdf"),
			new EventFile(2, 500, 1, "ANT", "BALLM", LocalDateTime.of(2020, 1, 2, 9, 0), "22222222.rtf", null));

		final var split = EventFiles.of(events, List.of(), Set.of(500));

		// The type of the event tells, for its document and its attachment alike (decision 60)
		assertThat(split.errandFiles().get(500)).extracting(row -> row.details().investigation()).containsExactly(true, true, false);
	}

	@Test
	void theFilesOfTheEventsGoToTheirErrandOrElseToTheirPlace() {
		final var events = List.of(
			new EventFile(1, 500, 1, "GTILL", "TDBV2", LocalDateTime.of(2020, 1, 1, 14, 35), "11111111.rtf", "ansokan.pdf"),
			new EventFile(2, null, 1, "UTSKR", "TDBBS", LocalDateTime.of(2020, 1, 2, 9, 0), "22222222.rtf", null),
			// An errand that is missing in Ärende
			new EventFile(3, 900, 1, "REMS", null, null, null, "remissvar.pdf"),
			new EventFile(4, null, null, null, "REMP", null, "33333333.rtf", "ingenstans.pdf"));
		final var pictures = List.of(new FileRow(500, "fasad.jpg", false), new FileRow(null, "utan-arende.jpg", false), new FileRow(500, null, false));

		final var split = EventFiles.of(events, pictures, Set.of(500));

		assertThat(split.errandFiles()).containsOnlyKeys(500);
		final var first = new FileDetails(null, LocalDateTime.of(2020, 1, 1, 14, 35));
		assertThat(split.errandFiles().get(500)).containsExactly(new FileRow(500, "11111111.rtf", true, first), new FileRow(500, "ansokan.pdf", false, first), new FileRow(500, "fasad.jpg", false));
		assertThat(split.placeFiles()).containsOnlyKeys(1);
		// The attachment of an event of a referral answer is one
		assertThat(split.placeFiles().get(1)).containsExactly(new FileRow(null, "22222222.rtf", true, new FileDetails(null, LocalDateTime.of(2020, 1, 2, 9, 0))),
			new FileRow(null, "remissvar.pdf", false, new FileDetails(FilePurposes.STATEMENT_RESPONSE, null, true)));
		assertThat(split.unplacedFiles()).containsOnlyKeys(4);
		// A document made from the template of a referral to the police is one
		assertThat(split.unplacedFiles().get(4)).containsExactly(new FileRow(null, "33333333.rtf", true, new FileDetails(FilePurposes.POLICE_REQUEST, null, true)), new FileRow(null, "ingenstans.pdf", false));
		assertThat(split.documents()).containsExactly(
			new DocumentRow(1, 500, 1, "TDBV2", LocalDateTime.of(2020, 1, 1, 14, 35), "11111111.rtf"),
			new DocumentRow(2, null, 1, "TDBBS", LocalDateTime.of(2020, 1, 2, 9, 0), "22222222.rtf"),
			new DocumentRow(4, null, null, "REMP", null, "33333333.rtf"));
	}

	@Test
	void theEventsGoToTheirErrandOrElseWithATextToTheirPlace() {
		final var withErrand = new TextRow(LocalDateTime.of(2020, 1, 1, 9, 0), "ANT", false, "på ärendet", true);
		final var missingErrand = new TextRow(null, "ANT", false, "ärendet saknas");
		final var withoutErrand = new TextRow(null, "HAND", false, "på stället");
		final var withoutErrandOrText = new TextRow(null, "HANU", false, null);
		final var nowhere = new TextRow(null, "HAND", false, "ingenstans");
		final var withoutText = new TextRow(LocalDateTime.of(2020, 1, 2, 9, 0), "REMP", false, null);
		final var picture = new TextRow(null, null, true, "bild");
		final var pictureWithoutText = new TextRow(null, null, true, null);
		final var pictureOfMissingErrand = new TextRow(null, null, true, "bild utan ärende");

		final var split = EventTexts.of(
			List.of(new EventText(500, 1, withErrand), new EventText(900, 1, missingErrand), new EventText(null, 1, withoutErrand), new EventText(null, 1, withoutErrandOrText),
				new EventText(null, null, nowhere), new EventText(500, 1, withoutText)),
			List.of(new EventText(500, null, picture), new EventText(500, null, pictureWithoutText), new EventText(901, null, pictureOfMissingErrand)),
			Set.of(500));

		// Every event of the errand makes the journal of its investigation, also one without a text, but a picture only with
		// one
		assertThat(split.errandTexts()).containsOnlyKeys(500);
		assertThat(split.errandTexts().get(500)).containsExactly(withErrand, withoutText, picture);
		// An event whose errand is missing belongs to its place, but only with a text; a picture has no place
		assertThat(split.placeTexts()).containsOnlyKeys(1);
		assertThat(split.placeTexts().get(1)).containsExactly(missingErrand, withoutErrand);
	}

	@ParameterizedTest
	@CsvSource(nullValues = "null", value = {
		"01, null",
		"' 01 ', null",
		"'', null",
		"null, null",
		"04, 04",
		"1, 1",
		"010, 010"
	})
	void theKindOfInspection01IsNoKind(final String written, final String expected) throws SQLException {
		final var rs = mock(ResultSet.class);
		when(rs.getString("Tillsynsform")).thenReturn(written);

		assertThat(AlktRepository.inspectionKind(rs)).isEqualTo(expected);
	}
}
