package se.sundsvall.aotloader.integration.partyassets.configuration;

import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.integration.NoResend;

/**
 * Gives the tests of another package the customizer of the configuration.
 */
public final class PartyAssetsConfigurationAccess {

	private PartyAssetsConfigurationAccess() {}

	public static FeignBuilderCustomizer customizer(final ClientRegistrationRepository repository, final int connectTimeout, final int readTimeout, final AotLoaderProperties properties,
		final NoResend noResend) {
		return new PartyAssetsConfiguration().feignBuilderCustomizer(repository, new PartyAssetsProperties(connectTimeout, readTimeout), properties, noResend);
	}
}
