package se.sundsvall.aotloader.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CleanupGuardTest {

	private static final String SUPPORT_MANAGEMENT = "https://api-test.example.com/supportmanagement/1.0";
	private static final String PARTY_ASSETS = "https://api-test.example.com/partyassets/3.0";
	private static final String RELATION = "https://relations.test.example.com/relation/1.0";

	@Test
	void aCleanupStartsWhenEveryTargetIsOnAnAllowedHost() {
		final var guard = new CleanupGuard(" API-TEST.example.com , relations.test.example.com,", SUPPORT_MANAGEMENT, PARTY_ASSETS, RELATION);

		assertThat(guard.check()).isEmpty();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = " , ")
	void withoutAllowedHostsNoCleanupStarts(final String allowedHosts) {
		final var guard = new CleanupGuard(allowedHosts, SUPPORT_MANAGEMENT, PARTY_ASSETS, RELATION);

		assertThat(guard.check()).contains("Rensningen tillåts bara mot en testmiljö, och ingen värd för den är angiven i "
			+ "aot-loader.cleanup.allowed-hosts (AOT_LOADER_CLEANUP_ALLOWED_HOSTS)");
	}

	@Test
	void aTargetOnAnotherHostStopsTheCleanup() {
		// The test settings copied for production, with the hosts of production
		final var guard = new CleanupGuard("api-test.example.com,relations.test.example.com", "https://api.example.com/supportmanagement/1.0", PARTY_ASSETS,
			"https://api.example.com/relation/1.0");

		assertThat(guard.check()).contains("Rensningen tillåts bara mot värdarna i aot-loader.cleanup.allowed-hosts (AOT_LOADER_CLEANUP_ALLOWED_HOSTS), "
			+ "och SupportManagement på api.example.com, relationstjänsten på api.example.com finns inte bland dem");
	}

	@Test
	void aTargetWithoutAHostStopsTheCleanup() {
		final var guard = new CleanupGuard("api-test.example.com", SUPPORT_MANAGEMENT, "not a url", "/relation");

		assertThat(guard.check()).contains("Rensningen tillåts bara mot värdarna i aot-loader.cleanup.allowed-hosts (AOT_LOADER_CLEANUP_ALLOWED_HOSTS), "
			+ "och PartyAssets på en adress utan värd, relationstjänsten på en adress utan värd finns inte bland dem");
	}

	@Test
	void theHostIsReadWithoutPortAndInLowerCase() {
		assertThat(CleanupGuard.hostOf(" http://LocalHost:8080/support-management ")).isEqualTo("localhost");
		assertThat(CleanupGuard.hostOf(null)).isEmpty();
	}
}
