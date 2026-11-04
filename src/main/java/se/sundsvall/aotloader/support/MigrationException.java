package se.sundsvall.aotloader.support;

import java.io.Serial;

/**
 * A failure the migration describes itself. Its message goes to the status table, the report and the log, so it must
 * never hold names or numbers of persons or companies.
 */
public class MigrationException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	public MigrationException(final String message) {
		super(message);
	}
}
