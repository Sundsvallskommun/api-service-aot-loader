package se.sundsvall.aotloader.integration.supportmanagement.configuration;

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

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Import(FeignConfiguration.class)
public class SupportManagementConfiguration {

	public static final String CLIENT_ID = "support-management";

	/** Keeps the migration's own writes from waking a process (R1). */
	public static final String TRIGGER_PROCESS_HEADER = "X-Trigger-Process";

	/** Keeps the migration's writes from notifying the subscribers of the errand (R3). */
	public static final String NOTIFY_HEADER = "X-notify";

	@Bean
	FeignBuilderCustomizer feignBuilderCustomizer(final ClientRegistrationRepository clientRepository, final SupportManagementProperties properties, final AotLoaderProperties aotLoaderProperties,
		final NoResendCapability noResend) {
		return FeignMultiCustomizer.create()
			// An errand that is gone is an answer: the migration then looks for it by its tag, or creates it. The decoder makes
			// every other status 502, so the statuses that refuse a file must be let through to be told apart.
			.withErrorDecoder(new ProblemErrorDecoder(CLIENT_ID, bypassedStatuses()))
			.withRequestTimeoutsInSeconds(properties.connectTimeout(), properties.readTimeout())
			.withRetryableOAuth2InterceptorForClientRegistration(clientRepository.findByRegistrationId(CLIENT_ID))
			// Every call carries the headers, so no call of the migration can wake a process or notify anyone
			.withRequestInterceptor(template -> template.header(TRIGGER_PROCESS_HEADER, "false"))
			.withRequestInterceptor(template -> template.header(NOTIFY_HEADER, "false"))
			.withRequestInterceptor(new SentByInterceptor(aotLoaderProperties.sentBy()))
			.withCustomizer(noResend.customizer())
			.composeCustomizersToOne();
	}

	static List<Integer> bypassedStatuses() {
		return Stream.concat(Stream.of(NOT_FOUND.value()), FileRefusal.STATUSES.stream()).sorted().toList();
	}
}
