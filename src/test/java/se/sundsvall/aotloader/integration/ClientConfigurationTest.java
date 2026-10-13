package se.sundsvall.aotloader.integration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import se.sundsvall.aotloader.integration.party.configuration.PartyConfigurationAccess;
import se.sundsvall.aotloader.integration.partyassets.configuration.PartyAssetsConfigurationAccess;
import se.sundsvall.aotloader.integration.relation.configuration.RelationConfigurationAccess;
import se.sundsvall.aotloader.integration.supportmanagement.configuration.SupportManagementConfigurationAccess;
import se.sundsvall.aotloader.service.TestData;
import se.sundsvall.dept44.configuration.feign.FeignMultiCustomizer;
import se.sundsvall.dept44.configuration.feign.decoder.ProblemErrorDecoder;
import se.sundsvall.dept44.security.Truststore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientConfigurationTest {

	@Mock
	private ClientRegistrationRepository clientRegistrationRepository;

	@Mock
	private ClientRegistration clientRegistration;

	@Mock
	private FeignBuilderCustomizer customizer;

	@Mock
	private ObjectProvider<Truststore> truststore;

	private NoResendCapability noResend;

	@BeforeEach
	void setUp() {
		noResend = new NoResendCapability(truststore);
	}

	@Test
	void supportManagementSendsTheHeadersThatKeepProcessesAndNotificationsAway() {
		final var template = customize("support-management", repository -> SupportManagementConfigurationAccess.customizer(repository, 10, 180, TestData.properties(), noResend), 3, true);

		assertThat(template.headers()).containsEntry("X-Trigger-Process", List.of("false"))
			.containsEntry("X-notify", List.of("false"))
			.containsEntry("X-Sent-By", List.of("alkt-migration; type=migration"));
	}

	@Test
	void partyAssetsSendsWhoTheActorIs() {
		final var template = customize("party-assets", repository -> PartyAssetsConfigurationAccess.customizer(repository, 10, 60, TestData.properties(), noResend), 1, true);

		assertThat(template.headers()).containsEntry("X-Sent-By", List.of("alkt-migration; type=migration")).doesNotContainKey("X-Trigger-Process");
	}

	@Test
	void relationSendsWhoTheActorIs() {
		final var template = customize("relation", repository -> RelationConfigurationAccess.customizer(repository, 10, 30, TestData.properties(), noResend), 1, true);

		assertThat(template.headers()).containsEntry("X-Sent-By", List.of("alkt-migration; type=migration"));
	}

	@Test
	void partySendsNoHeaders() {
		final var template = customize("party", repository -> PartyConfigurationAccess.customizer(repository, 10, 30), 0, false);

		assertThat(template.headers()).isEmpty();
	}

	/**
	 * Builds the customizer of a client and applies the request interceptors it adds to a request.
	 */
	private RequestTemplate customize(final String registrationId, final Function<ClientRegistrationRepository, FeignBuilderCustomizer> configuration, final int interceptors, final boolean noResendAdded) {
		final var spy = Mockito.spy(FeignMultiCustomizer.create());
		final var added = new ArrayList<RequestInterceptor>();
		when(clientRegistrationRepository.findByRegistrationId(registrationId)).thenReturn(clientRegistration);
		Mockito.lenient().doAnswer(invocation -> {
			added.add(invocation.getArgument(0));
			return spy;
		}).when(spy).withRequestInterceptor(any());
		when(spy.composeCustomizersToOne()).thenReturn(customizer);

		try (final MockedStatic<FeignMultiCustomizer> multiCustomizer = Mockito.mockStatic(FeignMultiCustomizer.class)) {
			multiCustomizer.when(FeignMultiCustomizer::create).thenReturn(spy);

			assertThat(configuration.apply(clientRegistrationRepository)).isSameAs(customizer);
		}

		verify(spy).withErrorDecoder(any(ProblemErrorDecoder.class));
		verify(spy).withRetryableOAuth2InterceptorForClientRegistration(same(clientRegistration));
		assertThat(added).hasSize(interceptors);
		verify(spy, times(noResendAdded ? 1 : 0)).withCustomizer(noResend.customizer());
		final var template = new RequestTemplate();
		added.forEach(interceptor -> interceptor.apply(template));
		return template;
	}
}
