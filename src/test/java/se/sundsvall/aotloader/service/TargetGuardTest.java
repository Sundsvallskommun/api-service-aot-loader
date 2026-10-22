package se.sundsvall.aotloader.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.aotloader.integration.db.MigrationTargetRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationTargetEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TargetGuardTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T08:00:00Z"), ZoneId.of("Europe/Stockholm"));
	private static final String SUPPORT_MANAGEMENT = "https://api-test.example.com/supportmanagement/1.0";
	private static final String PARTY_ASSETS = "https://api-test.example.com/partyassets/3.0";
	private static final String RELATION = "https://api-test.example.com/relation/1.0";

	@Mock
	private MigrationTargetRepository repository;

	@Test
	void theFirstRunBindsTheDatabase() {
		when(repository.findById(1)).thenReturn(Optional.empty());
		final var guard = new TargetGuard(repository, SUPPORT_MANAGEMENT + "/", " AOT ", PARTY_ASSETS + "/", RELATION, CLOCK);

		assertThat(guard.check()).isEmpty();

		final var captor = ArgumentCaptor.forClass(MigrationTargetEntity.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getId()).isEqualTo(1);
		assertThat(captor.getValue().getSupportManagementUrl()).isEqualTo(SUPPORT_MANAGEMENT);
		assertThat(captor.getValue().getNamespace()).isEqualTo("AOT");
		assertThat(captor.getValue().getPartyAssetsUrl()).isEqualTo(PARTY_ASSETS);
		assertThat(captor.getValue().getRelationUrl()).isEqualTo(RELATION);
		assertThat(captor.getValue().getCreated()).isEqualTo(CLOCK.instant().atZone(CLOCK.getZone()).toOffsetDateTime());
	}

	@Test
	void theSameEnvironmentIsAccepted() {
		when(repository.findById(1)).thenReturn(Optional.of(target("https://API-TEST.example.com/supportmanagement/1.0", "AOT", PARTY_ASSETS, RELATION)));

		assertThat(guard().check()).isEmpty();
		verify(repository, never()).save(any());
	}

	@Test
	void aDatabaseBoundBeforeTheOtherPartsWereRecordedGetsThem() {
		final var target = target(SUPPORT_MANAGEMENT, null, null, null);
		when(repository.findById(1)).thenReturn(Optional.of(target));

		assertThat(guard().check()).isEmpty();

		verify(repository).save(target);
		assertThat(target.getNamespace()).isEqualTo("AOT");
		assertThat(target.getPartyAssetsUrl()).isEqualTo(PARTY_ASSETS);
		assertThat(target.getRelationUrl()).isEqualTo(RELATION);
	}

	@Test
	void anotherSupportManagementIsRefused() {
		when(repository.findById(1)).thenReturn(Optional.of(target(SUPPORT_MANAGEMENT, "AOT", PARTY_ASSETS, RELATION)));

		assertThat(new TargetGuard(repository, "https://api.example.com/supportmanagement/1.0", "AOT", PARTY_ASSETS, RELATION, CLOCK).check())
			.hasValueSatisfying(problem -> assertThat(problem).startsWith("Den lokala databasen hör till en annan miljö (SupportManagement https://api-test.example.com/supportmanagement/1.0, inte https://api.example.com/supportmanagement/1.0)"));
		verify(repository, never()).save(any());
	}

	@Test
	void anotherNamespacePartyAssetsOrRelationServiceIsRefused() {
		when(repository.findById(1)).thenReturn(Optional.of(target(SUPPORT_MANAGEMENT, "AOT_TEST", "https://other/partyassets", "https://other/relation")));

		assertThat(guard().check())
			.hasValueSatisfying(problem -> assertThat(problem).startsWith("Den lokala databasen hör till en annan miljö (namnrymden AOT_TEST, inte AOT; "
				+ "PartyAssets https://other/partyassets, inte " + PARTY_ASSETS + "; relationstjänsten https://other/relation, inte " + RELATION + ")"));
		verify(repository, never()).save(any());
	}

	@Test
	void theNamespaceIsComparedWithItsCase() {
		when(repository.findById(1)).thenReturn(Optional.of(target(SUPPORT_MANAGEMENT, "aot", PARTY_ASSETS, RELATION)));

		assertThat(guard().check()).hasValueSatisfying(problem -> assertThat(problem).contains("namnrymden aot, inte AOT"));
	}

	@Test
	void normalize() {
		assertThat(TargetGuard.normalize(null)).isEmpty();
		assertThat(TargetGuard.normalize(" HTTP://X/// ")).isEqualTo("http://x");
	}

	private TargetGuard guard() {
		return new TargetGuard(repository, SUPPORT_MANAGEMENT, "AOT", PARTY_ASSETS, RELATION, CLOCK);
	}

	private static MigrationTargetEntity target(final String supportManagementUrl, final String namespace, final String partyAssetsUrl, final String relationUrl) {
		final var entity = new MigrationTargetEntity();
		entity.setId(1);
		entity.setSupportManagementUrl(supportManagementUrl);
		entity.setNamespace(namespace);
		entity.setPartyAssetsUrl(partyAssetsUrl);
		entity.setRelationUrl(relationUrl);
		return entity;
	}
}
