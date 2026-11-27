package se.sundsvall.aotloader.source.alkt;

import java.time.Clock;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;

/**
 * Gives the tests of another package a reader.
 */
public final class AlktReaderAccess {

	private AlktReaderAccess() {}

	public static AlktReader create(final AlktRepository repository, final AotLoaderProperties properties, final Clock clock) {
		return new AlktReader(repository, properties, clock);
	}
}
