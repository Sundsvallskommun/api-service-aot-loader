package se.sundsvall.aotloader.integration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import se.sundsvall.dept44.support.Identifier;

/**
 * Sends the migration as the only sender of the requests to the target systems. dept44 passes on the sender of the
 * incoming request, which
 * for a single item migrated through the API is the caller; the target systems read the first value, and would then see
 * a person instead of the migration.
 */
public class SentByInterceptor implements RequestInterceptor {

	private final String sentBy;

	public SentByInterceptor(final String sentBy) {
		this.sentBy = sentBy;
	}

	@Override
	public void apply(final RequestTemplate template) {
		template.removeHeader(Identifier.HEADER_NAME);
		template.header(Identifier.HEADER_NAME, sentBy);
	}
}
