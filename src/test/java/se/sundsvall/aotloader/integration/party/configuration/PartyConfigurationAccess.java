package se.sundsvall.aotloader.integration.party.configuration;

import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Gives the tests of another package the customizer of the configuration.
 */
public final class PartyConfigurationAccess {

	private PartyConfigurationAccess() {}

	public static FeignBuilderCustomizer customizer(final ClientRegistrationRepository repository, final int connectTimeout, final int readTimeout) {
		return new PartyConfiguration().feignBuilderCustomizer(repository, new PartyProperties(connectTimeout, readTimeout));
	}
}
