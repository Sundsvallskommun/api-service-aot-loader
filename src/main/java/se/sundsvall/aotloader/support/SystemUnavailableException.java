package se.sundsvall.aotloader.support;

import feign.RetryableException;
import java.io.IOException;
import java.io.Serial;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;

/**
 * A failure because a system the migration needs could not be reached: a target system, Party, the token endpoint or
 * the file area. The item is not finished, and a later run takes it again. Its message follows the rule of
 * {@link MigrationException}.
 */
public class SystemUnavailableException extends MigrationException {

	@Serial
	private static final long serialVersionUID = 1L;

	private static final int MAX_CAUSES = 10;

	public SystemUnavailableException(final String message) {
		super(message);
	}

	/**
	 * @return true when the error or one of its causes tells that a system could not be reached: this exception, an error
	 *         of the network, a timeout included, a call that Feign gave up, or a token that could not be had
	 */
	public static boolean isCauseOf(final Throwable error) {
		var cause = error;
		for (var depth = 0; cause != null && depth < MAX_CAUSES; depth++) {
			if (cause instanceof SystemUnavailableException || cause instanceof IOException || cause instanceof RetryableException || cause instanceof OAuth2AuthorizationException) {
				return true;
			}
			cause = cause.getCause() == cause ? null : cause.getCause();
		}
		return false;
	}
}
