package se.sundsvall.aotloader.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.integration.db.MigrationTargetRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationTargetEntity;

import static se.sundsvall.aotloader.integration.db.model.MigrationTargetEntity.SINGLE_ROW_ID;

/**
 * Keeps the local database to one target environment. The status table says what is migrated in the environment it
 * was written against; used against another, it would skip items that were never migrated there. The first run binds
 * the database to the SupportManagement it calls, and a run against another one is refused.
 */
@Component
public class TargetGuard {

	private final MigrationTargetRepository repository;
	private final String supportManagementUrl;
	private final Clock clock;

	TargetGuard(final MigrationTargetRepository repository, @Value("${integration.support-management.url}") final String supportManagementUrl, final Clock clock) {
		this.repository = repository;
		this.supportManagementUrl = normalize(supportManagementUrl);
		this.clock = clock;
	}

	/**
	 * Binds the database to the configured environment, the first time.
	 *
	 * @return a description of the problem when the database belongs to another environment, otherwise empty
	 */
	public synchronized Optional<String> check() {
		final var target = repository.findById(SINGLE_ROW_ID).orElse(null);
		if (target == null) {
			final var entity = new MigrationTargetEntity();
			entity.setId(SINGLE_ROW_ID);
			entity.setSupportManagementUrl(supportManagementUrl);
			entity.setCreated(OffsetDateTime.now(clock));
			repository.save(entity);
			return Optional.empty();
		}
		if (supportManagementUrl.equals(normalize(target.getSupportManagementUrl()))) {
			return Optional.empty();
		}
		return Optional.of(("Den lokala databasen hör till en annan miljö (SupportManagement %s, inte %s). Använd en egen databas per miljö "
			+ "(AOT_LOADER_DB_URL), annars hoppar körningen över ärenden som bara är migrerade i den andra miljön.").formatted(target.getSupportManagementUrl(), supportManagementUrl));
	}

	static String normalize(final String url) {
		return Optional.ofNullable(url).map(String::strip).map(value -> value.replaceAll("/+$", "")).map(value -> value.toLowerCase(Locale.ROOT)).orElse("");
	}
}
