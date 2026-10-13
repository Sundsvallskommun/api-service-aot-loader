package se.sundsvall.aotloader.support;

import java.io.Serial;

/**
 * A failure the migration describes itself. Its message is written to the status table and the report as it is, so it
 * must never hold names or numbers of persons or companies.
 */
public class MigrationException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	public MigrationException(final String message) {
		super(message);
	}
}
