package se.sundsvall.aotloader.integration.supportmanagement.configuration;

import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.integration.Fallbacks;
import se.sundsvall.aotloader.integration.NoResend;
import se.sundsvall.aotloader.integration.SentByInterceptor;
import se.sundsvall.dept44.configuration.feign.FeignConfiguration;
import se.sundsvall.dept44.configuration.feign.FeignMultiCustomizer;
import se.sundsvall.dept44.configuration.feign.decoder.ProblemErrorDecoder;

@Import(FeignConfiguration.class)
public class SupportManagementConfiguration {

	public static final String CLIENT_ID = "support-management";

	/** Keeps the migration's own writes from waking a process (R1). */
	public static final String TRIGGER_PROCESS_HEADER = "X-Trigger-Process";

	/** Keeps the migration's writes from notifying the subscribers of the errand (R3). */
	public static final String NOTIFY_HEADER = "X-notify";

	@Bean
	FeignBuilderCustomizer feignBuilderCustomizer(final ClientRegistrationRepository clientRepository, final SupportManagementProperties properties, final AotLoaderProperties aotLoaderProperties,
		final NoResend noResend) {
		return FeignMultiCustomizer.create()
			// An errand that is gone is an answer: the migration then looks for it by its tag, or creates it. So is an attachment
			// already linked to the investigation. The decoder makes every other status 502, so these and the statuses that
			// refuse a file must be let through to be told apart.
			.withErrorDecoder(new ProblemErrorDecoder(CLIENT_ID, Fallbacks.BYPASSED_STATUSES))
			.withRequestTimeoutsInSeconds(properties.connectTimeout(), properties.readTimeout())
			.withRetryableOAuth2InterceptorForClientRegistration(clientRepository.findByRegistrationId(CLIENT_ID))
			// Every call carries the headers, so that the migration's own calls start no process and write no notice. They hold
			// for the client of the gateway, not for an AD account, and activating an errand still runs the active actions (R6)
			.withRequestInterceptor(template -> template.header(TRIGGER_PROCESS_HEADER, "false"))
			.withRequestInterceptor(template -> template.header(NOTIFY_HEADER, "false"))
			.withRequestInterceptor(new SentByInterceptor(aotLoaderProperties.sentBy()))
			.withCustomizer(noResend.customizer())
			.composeCustomizersToOne();
	}
}
