package se.sundsvall.aotloader.service;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static java.util.function.Predicate.not;

/**
 * Keeps the cleanup to the test environments (section 10.3). A cleanup removes what the migration wrote, so it starts
 * only when SupportManagement, PartyAssets and the relation service are all on a host the setting
 * aot-loader.cleanup.allowed-hosts names. The hosts of production are never among them, so a cleanup left turned on
 * in settings copied for production is refused there.
 */
@Component
class CleanupGuard {

	static final String SETTING = "aot-loader.cleanup.allowed-hosts (AOT_LOADER_CLEANUP_ALLOWED_HOSTS)";

	private final Set<String> allowedHosts;
	private final List<Target> targets;

	CleanupGuard(@Value("${aot-loader.cleanup.allowed-hosts:}") final String allowedHosts,
		@Value("${integration.support-management.url}") final String supportManagementUrl,
		@Value("${integration.party-assets.url}") final String partyAssetsUrl,
		@Value("${integration.relation.url}") final String relationUrl) {

		this.allowedHosts = Arrays.stream(Optional.ofNullable(allowedHosts).orElse("").split(","))
			.map(host -> host.strip().toLowerCase(Locale.ROOT))
			.filter(not(String::isEmpty))
			.collect(Collectors.toUnmodifiableSet());
		this.targets = List.of(
			new Target("SupportManagement", hostOf(supportManagementUrl)),
			new Target("PartyAssets", hostOf(partyAssetsUrl)),
			new Target("relationstjänsten", hostOf(relationUrl)));
	}

	/**
	 * @return a description of why a cleanup may not start against the target systems, empty when it may
	 */
	Optional<String> check() {
		if (allowedHosts.isEmpty()) {
			return Optional.of("Rensningen tillåts bara mot en testmiljö, och ingen värd för den är angiven i " + SETTING);
		}
		final var refused = targets.stream()
			.filter(target -> target.host().isEmpty() || !allowedHosts.contains(target.host()))
			.map(target -> "%s på %s".formatted(target.name(), target.host().isEmpty() ? "en adress utan värd" : target.host()))
			.toList();
		if (refused.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of("Rensningen tillåts bara mot värdarna i %s, och %s finns inte bland dem".formatted(SETTING, String.join(", ", refused)));
	}

	/**
	 * @return the host of the URL in lower case, blank when it has none or cannot be read
	 */
	static String hostOf(final String url) {
		try {
			return Optional.ofNullable(url).map(String::strip).map(URI::create).map(URI::getHost).map(host -> host.toLowerCase(Locale.ROOT)).orElse("");
		} catch (final IllegalArgumentException _) {
			return "";
		}
	}

	private record Target(String name, String host) {
	}
}
