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

import static se.sundsvall.aotloader.integration.supportmanagement.configuration.SupportManagementConfiguration.CLIENT_ID;
import static se.sundsvall.aotloader.integration.supportmanagement.configuration.SupportManagementConfiguration.NOTIFY_HEADER;
import static se.sundsvall.aotloader.integration.supportmanagement.configuration.SupportManagementConfiguration.TRIGGER_PROCESS_HEADER;

/**
 * The client of the decisions has the configuration of {@link SupportManagementConfiguration}, but is sent by the AD
 * account of the decisions (decision 70). Without one it is sent by the migration, which SupportManagement refuses a
 * manual decision, and the preflight stops a real run before that.
 */
@Import(FeignConfiguration.class)
public class SupportManagementDecisionConfiguration {

	public static final String DECISION_CLIENT_ID = "support-management-decisions";

	@Bean
	FeignBuilderCustomizer feignBuilderCustomizer(final ClientRegistrationRepository clientRepository, final SupportManagementProperties properties, final AotLoaderProperties aotLoaderProperties,
		final NoResend noResend) {
		return FeignMultiCustomizer.create()
			.withErrorDecoder(new ProblemErrorDecoder(CLIENT_ID, Fallbacks.BYPASSED_STATUSES))
			.withRequestTimeoutsInSeconds(properties.connectTimeout(), properties.readTimeout())
			.withRetryableOAuth2InterceptorForClientRegistration(clientRepository.findByRegistrationId(CLIENT_ID))
			// The headers of every other call. SupportManagement heeds X-notify from an AD account too, but not X-Trigger-Process;
			// no process hears of the decisions all the same, since they are written to a draft with the label that blocks
			// processes (decision 17)
			.withRequestInterceptor(template -> template.header(TRIGGER_PROCESS_HEADER, "false"))
			.withRequestInterceptor(template -> template.header(NOTIFY_HEADER, "false"))
			.withRequestInterceptor(new SentByInterceptor(aotLoaderProperties.decisionSentBy().orElse(aotLoaderProperties.sentBy())))
			.withCustomizer(noResend.customizer())
			.composeCustomizersToOne();
	}
}
