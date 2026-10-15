package se.sundsvall.aotloader.integration.partyassets.configuration;

import java.util.List;
import java.util.stream.Stream;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.integration.FileRefusal;
import se.sundsvall.aotloader.integration.NoResendCapability;
import se.sundsvall.aotloader.integration.SentByInterceptor;
import se.sundsvall.dept44.configuration.feign.FeignConfiguration;
import se.sundsvall.dept44.configuration.feign.FeignMultiCustomizer;
import se.sundsvall.dept44.configuration.feign.decoder.ProblemErrorDecoder;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Import(FeignConfiguration.class)
public class PartyAssetsConfiguration {

	public static final String CLIENT_ID = "party-assets";

	@Bean
	FeignBuilderCustomizer feignBuilderCustomizer(final ClientRegistrationRepository clientRepository, final PartyAssetsProperties properties, final AotLoaderProperties aotLoaderProperties,
		final NoResendCapability noResend) {
		return FeignMultiCustomizer.create()
			// 409 means the permit already exists, which the migration answers by reading it, and a permit that is gone is an
			// answer too: the migration then looks for it, or creates it. The answers that refuse a file are told apart as
			// well. The decoder makes every other status 502.
			.withErrorDecoder(new ProblemErrorDecoder(CLIENT_ID, bypassedStatuses()))
			.withRequestTimeoutsInSeconds(properties.connectTimeout(), properties.readTimeout())
			.withRetryableOAuth2InterceptorForClientRegistration(clientRepository.findByRegistrationId(CLIENT_ID))
			.withRequestInterceptor(new SentByInterceptor(aotLoaderProperties.sentBy()))
			.withCustomizer(noResend.customizer())
			.composeCustomizersToOne();
	}

	static List<Integer> bypassedStatuses() {
		return Stream.concat(Stream.of(NOT_FOUND.value(), CONFLICT.value()), FileRefusal.STATUSES.stream()).sorted().toList();
	}
}
