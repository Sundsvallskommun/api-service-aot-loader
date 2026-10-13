package se.sundsvall.aotloader.integration.partyassets.configuration;

import java.util.List;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.integration.NoResendCapability;
import se.sundsvall.aotloader.integration.SentByInterceptor;
import se.sundsvall.dept44.configuration.feign.FeignConfiguration;
import se.sundsvall.dept44.configuration.feign.FeignMultiCustomizer;
import se.sundsvall.dept44.configuration.feign.decoder.ProblemErrorDecoder;

import static org.springframework.http.HttpStatus.CONFLICT;

@Import(FeignConfiguration.class)
public class PartyAssetsConfiguration {

	public static final String CLIENT_ID = "party-assets";

	@Bean
	FeignBuilderCustomizer feignBuilderCustomizer(final ClientRegistrationRepository clientRepository, final PartyAssetsProperties properties, final AotLoaderProperties aotLoaderProperties,
		final NoResendCapability noResend) {
		return FeignMultiCustomizer.create()
			// 409 means the permit already exists, which the migration answers by reading it
			.withErrorDecoder(new ProblemErrorDecoder(CLIENT_ID, List.of(CONFLICT.value())))
			.withRequestTimeoutsInSeconds(properties.connectTimeout(), properties.readTimeout())
			.withRetryableOAuth2InterceptorForClientRegistration(clientRepository.findByRegistrationId(CLIENT_ID))
			.withRequestInterceptor(new SentByInterceptor(aotLoaderProperties.sentBy()))
			.withCustomizer(noResend.customizer())
			.composeCustomizersToOne();
	}
}
