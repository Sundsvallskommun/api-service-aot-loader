package se.sundsvall.aotloader.source.alkt;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.source.alkt.AlktRepository.EventFile;
import se.sundsvall.aotloader.source.alkt.AlktRepository.EventFiles;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.DocumentRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.FileRow;

import static org.assertj.core.api.Assertions.assertThat;

class AlktRepositoryTest {

	@Test
	void theFilesOfTheEventsGoToTheirErrandOrElseToTheirPlace() {
		final var events = List.of(
			new EventFile(1, 500, 1, "TDBV2", LocalDate.of(2020, 1, 1), "11111111.rtf", "ansokan.pdf"),
			new EventFile(2, null, 1, "TDBBS", LocalDate.of(2020, 1, 2), "22222222.rtf", null),
			// An errand that is missing in Ärende
			new EventFile(3, 900, 1, null, null, null, "anmalan.pdf"),
			new EventFile(4, null, null, null, null, "33333333.rtf", "ingenstans.pdf"));
		final var pictures = List.of(new FileRow(500, "fasad.jpg", false), new FileRow(null, "utan-arende.jpg", false), new FileRow(500, null, false));

		final var split = EventFiles.of(events, pictures, Set.of(500));

		assertThat(split.errandFiles()).containsOnlyKeys(500);
		assertThat(split.errandFiles().get(500)).containsExactly(new FileRow(500, "11111111.rtf", true), new FileRow(500, "ansokan.pdf", false), new FileRow(500, "fasad.jpg", false));
		assertThat(split.placeFiles()).containsOnlyKeys(1);
		assertThat(split.placeFiles().get(1)).containsExactly(new FileRow(null, "22222222.rtf", true), new FileRow(null, "anmalan.pdf", false));
		assertThat(split.unplacedFiles()).containsOnlyKeys(4);
		assertThat(split.unplacedFiles().get(4)).containsExactly(new FileRow(null, "33333333.rtf", true), new FileRow(null, "ingenstans.pdf", false));
		assertThat(split.documents()).containsExactly(
			new DocumentRow(1, 500, 1, "TDBV2", LocalDate.of(2020, 1, 1), "11111111.rtf"),
			new DocumentRow(2, null, 1, "TDBBS", LocalDate.of(2020, 1, 2), "22222222.rtf"),
			new DocumentRow(4, null, null, null, null, "33333333.rtf"));
	}
}
