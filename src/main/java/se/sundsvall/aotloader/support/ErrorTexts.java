package se.sundsvall.aotloader.support;

import java.util.Optional;
import java.util.regex.Pattern;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static java.util.Objects.requireNonNullElse;

/**
 * Describes errors for the status table, the report and the log.
 */
public final class ErrorTexts {

	// A URL in an error, as in "Read timed out executing GET http://host/path?partyId=...": its query holds values the
	// scrubber cannot judge, and is left out
	private static final Pattern URL_QUERY = Pattern.compile("(\\bhttps?://[^\\s?#]*)[?#]\\S*");
	private static final int MAX_CAUSES = 10;

	private ErrorTexts() {}

	/**
	 * @return the error described for the business: the message of a {@link MigrationException} as it is, and any other
	 *         error with the queries of URLs left out and numbers and email addresses masked
	 */
	public static String describe(final Throwable e) {
		return switch (e) {
			case final MigrationException migration -> migration.getMessage();
			case final ThrowableProblem problem -> scrub("%s %s".formatted(problem.getStatus(), requireNonNullElse(problem.getDetail(), requireNonNullElse(problem.getTitle(), ""))).strip());
			default -> scrub(e.getClass().getSimpleName() + Optional.ofNullable(e.getMessage()).map(message -> ": " + message).orElse(""));
		};
	}

	/**
	 * @return a copy of the error for the log, with its stack trace and its causes, up to ten, whose messages name the
	 *         class of each and describe it as {@link #describe} does
	 */
	public static Throwable forLog(final Throwable e) {
		return forLog(e, 0);
	}

	private static Throwable forLog(final Throwable e, final int depth) {
		final var cause = e.getCause() == null || e.getCause() == e || depth >= MAX_CAUSES ? null : forLog(e.getCause(), depth + 1);
		final var copy = new LoggedError(e.getClass().getName() + ": " + describe(e), cause);
		copy.setStackTrace(e.getStackTrace());
		return copy;
	}

	private static String scrub(final String text) {
		return PiiScrubber.scrub(URL_QUERY.matcher(text).replaceAll("$1"));
	}

	private static final class LoggedError extends RuntimeException {

		private static final long serialVersionUID = 1L;

		LoggedError(final String message, final Throwable cause) {
			super(message, cause, false, true);
		}
	}
}
