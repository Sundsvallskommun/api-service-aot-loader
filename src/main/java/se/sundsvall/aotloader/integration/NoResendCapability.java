package se.sundsvall.aotloader.integration;

import feign.Capability;
import feign.Client;
import feign.Request.HttpMethod;
import feign.RetryableException;
import feign.Retryer;
import feign.okhttp.OkHttpClient;
import java.io.IOException;
import java.util.Set;
import javax.net.ssl.X509TrustManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.stereotype.Component;
import se.sundsvall.dept44.security.Truststore;

/**
 * Keeps a client from sending a request that may have changed something a second time. A create that reached the
 * target before the connection broke would otherwise be made twice; it fails instead, and the next run finds what the
 * first one created. Two layers send again on their own and are both stopped:
 * <ul>
 * <li>OkHttp sends a request again after a connection failure, whatever its method. The client is replaced by one built
 * as dept44 builds it, with the same trust store, that does not.</li>
 * <li>Feign makes every IO error, a read timeout included, retryable, and the retryer of dept44 then sends the request
 * again. Only a request that changes nothing is let through to it; a request refused with 401 still is, since it
 * changed nothing.</li>
 * </ul>
 */
@Component
public class NoResendCapability implements Capability {

	private static final Set<HttpMethod> SAFE_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);

	private final Client client;
	private final FeignBuilderCustomizer customizer = builder -> builder.addCapability(this);

	public NoResendCapability(final ObjectProvider<Truststore> truststore) {
		final var builder = new okhttp3.OkHttpClient.Builder().retryOnConnectionFailure(false);
		truststore.ifAvailable(store -> builder.sslSocketFactory(store.getSSLContext().getSocketFactory(), (X509TrustManager) store.getTrustManagerFactory().getTrustManagers()[0]));
		this.client = new OkHttpClient(builder.build());
	}

	/**
	 * @return the customizer that adds the capability to a client
	 */
	public FeignBuilderCustomizer customizer() {
		return customizer;
	}

	@Override
	public Client enrich(final Client ignored) {
		return client;
	}

	@Override
	public Retryer enrich(final Retryer retryer) {
		return new NoResendRetryer(retryer);
	}

	record NoResendRetryer(Retryer delegate)
		implements
		Retryer {

		@Override
		public void continueOrPropagate(final RetryableException e) {
			if (e.getCause() instanceof IOException && !SAFE_METHODS.contains(e.method())) {
				throw e;
			}
			delegate.continueOrPropagate(e);
		}

		@Override
		public Retryer clone() { // NOSONAR java:S2975, the Retryer of Feign requires clone
			return new NoResendRetryer(delegate.clone());
		}
	}
}
