package se.sundsvall.aotloader.integration;

import feign.Capability;
import feign.Client;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RetryableException;
import feign.Retryer;
import feign.okhttp.OkHttpClient;
import java.io.IOException;
import java.util.Set;
import javax.net.ssl.X509TrustManager;
import se.sundsvall.dept44.security.Truststore;

import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;

/**
 * Keeps a client from sending a request that may have changed something a second time. A create that reached the
 * target before the connection broke would otherwise be made twice; it fails instead, and the next run finds what the
 * first one created. Two layers send again on their own and are both stopped:
 * <ul>
 * <li>OkHttp sends a request again after a connection failure, or answered with 503 and Retry-After 0, and follows a
 * redirect, whatever its method. The client is replaced by one built as dept44 builds it, with the same trust store,
 * that does none of that; a redirect is returned as the response.</li>
 * <li>Feign makes every IO error, a read timeout included, retryable. The retryer of dept44 sends again only a
 * request refused with 401, which changed nothing, and the capability keeps any retryer from sending a request
 * that may have changed something again after an IO error.</li>
 * </ul>
 * It is added to a client through {@link NoResend}, never as a bean of its own.
 */
public class NoResendCapability implements Capability {

	private static final Set<HttpMethod> SAFE_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);
	private static final String RETRY_AFTER = "Retry-After";

	private final Client client;

	/**
	 * @param truststore the trust store of dept44, null when there is none
	 */
	public NoResendCapability(final Truststore truststore) {
		final var builder = new okhttp3.OkHttpClient.Builder()
			.retryOnConnectionFailure(false)
			.followRedirects(false)
			.followSslRedirects(false)
			.addNetworkInterceptor(chain -> withoutRetryAfter(chain.request(), chain.proceed(chain.request())));
		if (truststore != null) {
			builder.sslSocketFactory(truststore.getSSLContext().getSocketFactory(), (X509TrustManager) truststore.getTrustManagerFactory().getTrustManagers()[0]);
		}
		this.client = new OkHttpClient(builder.build());
	}

	@Override
	public Client enrich(final Client ignored) {
		return client;
	}

	@Override
	public Retryer enrich(final Retryer retryer) {
		return new NoResendRetryer(retryer);
	}

	/**
	 * @return the options with redirects not followed, since the client of Feign follows them as its options say
	 */
	@Override
	public Request.Options enrich(final Request.Options options) {
		return new Request.Options(options.connectTimeout(), options.connectTimeoutUnit(), options.readTimeout(), options.readTimeoutUnit(), false);
	}

	/**
	 * @return the response without Retry-After when it is a 503 to a request that may have changed something, which OkHttp
	 *         would otherwise send again at once for Retry-After 0
	 */
	static okhttp3.Response withoutRetryAfter(final okhttp3.Request request, final okhttp3.Response response) {
		if (response.code() == HTTP_UNAVAILABLE && SAFE_METHODS.stream().noneMatch(method -> method.name().equals(request.method()))) {
			return response.newBuilder().removeHeader(RETRY_AFTER).build();
		}
		return response;
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
