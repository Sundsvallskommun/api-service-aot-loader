package se.sundsvall.aotloader.source;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.service.TestData;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.source.alkol.AlkolReaderAccess;
import se.sundsvall.aotloader.source.alkol.AlkolRepository;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot;
import se.sundsvall.aotloader.source.alkt.AlktReaderAccess;
import se.sundsvall.aotloader.source.alkt.AlktRepository;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static se.sundsvall.aotloader.service.model.Kind.ANMALAN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYN;

class ReadersTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T08:00:00Z"), ZoneId.of("Europe/Stockholm"));

	@Test
	void alktReader() {
		final var repository = mock(AlktRepository.class);
		final var permit = new AlktSnapshot.PermitRow(1, null, true, false, false, false, false, true, null, null, null, null, false, false, false, false, false, false, false, false, false, true, false, List.of(), null, null, null,
			List.of(), null, null, null, "IAN-1", null, null);
		final var errand = new AlktSnapshot.ErrandRow(700, 1, "07", null, null, null, null);
		when(repository.readSnapshot()).thenReturn(new AlktSnapshot(List.of(permit), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(700, errand), Map.of(), Map.of(), Map.of(), Map.of()));
		final var reader = AlktReaderAccess.create(repository, TestData.properties(), CLOCK);

		assertThat(reader.source()).isEqualTo(Source.ALKT);
		assertThat(reader.kinds()).containsExactlyInAnyOrder(TILLSTAND, TILLSYN);
		assertThat(reader.read(Set.of(TILLSTAND, TILLSYN)).candidates()).extracting(candidate -> candidate.key().kind()).containsExactly(TILLSTAND, TILLSYN);
		assertThat(reader.read(Set.of(TILLSYN)).candidates()).extracting(candidate -> candidate.key().kind()).containsExactly(TILLSYN);
		assertThat(reader.read(Set.of()).candidates()).isEmpty();
	}

	@Test
	void alkolReader() {
		final var repository = mock(AlkolRepository.class);
		final var place = new AlkolSnapshot.SalesPlaceRow(1, "Butik", null, null, null, null, null, null, null, null, null, true, true, false, false, false, null);
		when(repository.readSnapshot()).thenReturn(new AlkolSnapshot(List.of(place), Map.of(1, new AlkolSnapshot.PermitViewRow(1, true, false, false)), Map.of(),
			List.of(new AlkolSnapshot.ErrandRow(400, 1, "IT", null, null, null, null)), List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()));
		final var reader = AlkolReaderAccess.create(repository, TestData.properties(), CLOCK);

		assertThat(reader.source()).isEqualTo(Source.ALKOL);
		assertThat(reader.kinds()).containsExactlyInAnyOrder(Kind.values());
		assertThat(reader.read(Set.of(Kind.values())).candidates()).extracting(candidate -> candidate.key().kind()).containsExactly(TILLSTAND, ANMALAN, TILLSYN);
		assertThat(reader.read(Set.of(ANMALAN)).candidates()).extracting(candidate -> candidate.key().sourceId()).containsExactly("1-FOLKOL");
	}
}
