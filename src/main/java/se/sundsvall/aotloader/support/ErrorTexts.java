package se.sundsvall.aotloader.support;

import java.util.Optional;
import se.sundsvall.dept44.problem.ThrowableProblem;

/**
 * Describes errors for the status table, the report and the log.
 */
public final class ErrorTexts {

	private ErrorTexts() {}

	/**
	 * @return the error described for the business, with numbers and email addresses masked
	 */
	public static String describe(final Throwable e) {
		return switch (e) {
			case final MigrationException migration -> migration.getMessage();
			case final ThrowableProblem problem -> PiiScrubber.scrub("%s %s".formatted(problem.getStatus(), Optional.ofNullable(problem.getDetail()).orElse(Optional.ofNullable(problem.getTitle()).orElse(""))).strip());
			default -> PiiScrubber.scrub(e.getClass().getSimpleName() + Optional.ofNullable(e.getMessage()).map(message -> ": " + message).orElse(""));
		};
	}
}
