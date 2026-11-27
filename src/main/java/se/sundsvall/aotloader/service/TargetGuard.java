package se.sundsvall.aotloader.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.integration.db.MigrationTargetRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationTargetEntity;

import static se.sundsvall.aotloader.integration.db.model.MigrationTargetEntity.SINGLE_ROW_ID;

/**
 * Keeps the local database to one target environment. The status table says what is migrated, and holds the ids of
 * errands, posts and relations, in the environment it was written against; used against another, it would skip items
 * that were never migrated there. The first run binds the database to the SupportManagement and namespace, the
 * PartyAssets and the relation service it calls, and a run against another one is refused. A part the database was
 * bound without is bound at the next run.
 */
@Component
public class TargetGuard {

	private final MigrationTargetRepository repository;
	private final List<Part> parts;
	private final Clock clock;

	TargetGuard(final MigrationTargetRepository repository,
		@Value("${integration.support-management.url}") final String supportManagementUrl,
		@Value("${aot-loader.namespace}") final String namespace,
		@Value("${integration.party-assets.url}") final String partyAssetsUrl,
		@Value("${integration.relation.url}") final String relationUrl,
		final Clock clock) {

		this.repository = repository;
		this.parts = List.of(
			new Part("SupportManagement", normalize(supportManagementUrl), TargetGuard::normalize, MigrationTargetEntity::getSupportManagementUrl, MigrationTargetEntity::setSupportManagementUrl),
			new Part("namnrymden", strip(namespace), TargetGuard::strip, MigrationTargetEntity::getNamespace, MigrationTargetEntity::setNamespace),
			new Part("PartyAssets", normalize(partyAssetsUrl), TargetGuard::normalize, MigrationTargetEntity::getPartyAssetsUrl, MigrationTargetEntity::setPartyAssetsUrl),
			new Part("relationstjänsten", normalize(relationUrl), TargetGuard::normalize, MigrationTargetEntity::getRelationUrl, MigrationTargetEntity::setRelationUrl));
		this.clock = clock;
	}

	/**
	 * Binds the database to the configured environment, the first time, and each part it was bound without.
	 *
	 * @return a description of the problem when the database belongs to another environment, otherwise empty
	 */
	public synchronized Optional<String> check() {
		final var target = repository.findById(SINGLE_ROW_ID).orElseGet(this::newTarget);
		final var differences = parts.stream()
			.filter(part -> part.bound(target) != null && !part.configured().equals(part.normalizer().apply(part.bound(target))))
			.map(part -> "%s %s, inte %s".formatted(part.name(), part.bound(target), part.configured()))
			.toList();
		if (!differences.isEmpty()) {
			return Optional.of(("Den lokala databasen hör till en annan miljö (%s). Använd en egen databas per miljö (AOT_LOADER_DB_URL), annars hoppar "
				+ "körningen över ärenden som bara är migrerade i den andra miljön.").formatted(String.join("; ", differences)));
		}
		final var unbound = parts.stream().filter(part -> part.bound(target) == null).toList();
		if (!unbound.isEmpty()) {
			unbound.forEach(part -> part.binder().accept(target, part.configured()));
			repository.save(target);
		}
		return Optional.empty();
	}

	private MigrationTargetEntity newTarget() {
		final var entity = new MigrationTargetEntity();
		entity.setId(SINGLE_ROW_ID);
		entity.setCreated(OffsetDateTime.now(clock));
		return entity;
	}

	static String normalize(final String url) {
		final var stripped = strip(url);
		var end = stripped.length();
		while (end > 0 && stripped.charAt(end - 1) == '/') {
			end--;
		}
		return stripped.substring(0, end).toLowerCase(Locale.ROOT);
	}

	private static String strip(final String value) {
		return value == null ? "" : value.strip();
	}

	private record Part(String name, String configured, UnaryOperator<String> normalizer, Function<MigrationTargetEntity, String> getter, BiConsumer<MigrationTargetEntity, String> binder) {

		String bound(final MigrationTargetEntity target) {
			return getter.apply(target);
		}
	}
}
