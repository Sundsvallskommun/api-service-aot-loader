package se.sundsvall.aotloader.source.alkol;

import java.time.Clock;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;

/**
 * Gives the tests of another package a reader.
 */
public final class AlkolReaderAccess {

	private AlkolReaderAccess() {}

	public static AlkolReader create(final AlkolRepository repository, final AotLoaderProperties properties, final Clock clock) {
		return new AlkolReader(repository, properties, clock);
	}
}
