package se.sundsvall.aotloader.integration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.security.Truststore;

/**
 * Adds the {@link NoResendCapability} to the clients that write: SupportManagement, PartyAssets and Relation. It is no
 * Capability itself, since Spring Cloud OpenFeign adds every Capability bean to every client, and Party, which only
 * reads, would then not send a request again after a connection the gateway closed.
 */
@Component
public class NoResend {

	private final FeignBuilderCustomizer customizer;

	public NoResend(final ObjectProvider<Truststore> truststore) {
		final var capability = new NoResendCapability(truststore.getIfAvailable());
		this.customizer = builder -> builder.addCapability(capability);
	}

	/**
	 * @return the customizer that adds the capability to a client
	 */
	public FeignBuilderCustomizer customizer() {
		return customizer;
	}
}
