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

	@Mock
	private MigrationTargetRepository repository;

	@Test
	void theFirstRunBindsTheDatabase() {
		when(repository.findById(1)).thenReturn(Optional.empty());
		final var guard = new TargetGuard(repository, "https://api-test.example.com/supportmanagement/1.0/", CLOCK);

		assertThat(guard.check()).isEmpty();

		final var captor = ArgumentCaptor.forClass(MigrationTargetEntity.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getId()).isEqualTo(1);
		assertThat(captor.getValue().getSupportManagementUrl()).isEqualTo("https://api-test.example.com/supportmanagement/1.0");
		assertThat(captor.getValue().getCreated()).isNotNull();
	}

	@Test
	void theSameEnvironmentIsAccepted() {
		when(repository.findById(1)).thenReturn(Optional.of(target("https://API-TEST.example.com/supportmanagement/1.0")));

		assertThat(new TargetGuard(repository, "https://api-test.example.com/supportmanagement/1.0/", CLOCK).check()).isEmpty();
		verify(repository, never()).save(any());
	}

	@Test
	void anotherEnvironmentIsRefused() {
		when(repository.findById(1)).thenReturn(Optional.of(target("https://api-test.example.com/supportmanagement/1.0")));

		assertThat(new TargetGuard(repository, "https://api.example.com/supportmanagement/1.0", CLOCK).check())
			.hasValueSatisfying(problem -> assertThat(problem).startsWith("Den lokala databasen hör till en annan miljö (SupportManagement https://api-test.example.com/supportmanagement/1.0, inte https://api.example.com/supportmanagement/1.0)"));
	}

	@Test
	void normalize() {
		assertThat(TargetGuard.normalize(null)).isEmpty();
		assertThat(TargetGuard.normalize(" HTTP://X/// ")).isEqualTo("http://x");
	}

	private static MigrationTargetEntity target(final String url) {
		final var entity = new MigrationTargetEntity();
		entity.setId(1);
		entity.setSupportManagementUrl(url);
		return entity;
	}
}
