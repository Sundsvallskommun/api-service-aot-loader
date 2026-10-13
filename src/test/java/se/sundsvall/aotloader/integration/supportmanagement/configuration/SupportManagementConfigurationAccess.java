package se.sundsvall.aotloader.integration.supportmanagement.configuration;

import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.integration.NoResendCapability;

/**
 * Gives the tests of another package the customizer of the configuration.
 */
public final class SupportManagementConfigurationAccess {

	private SupportManagementConfigurationAccess() {}

	public static FeignBuilderCustomizer customizer(final ClientRegistrationRepository repository, final int connectTimeout, final int readTimeout, final AotLoaderProperties properties,
		final NoResendCapability noResend) {
		return new SupportManagementConfiguration().feignBuilderCustomizer(repository, new SupportManagementProperties(connectTimeout, readTimeout), properties, noResend);
	}
}
