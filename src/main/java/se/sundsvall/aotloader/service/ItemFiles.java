package se.sundsvall.aotloader.service;

import java.io.IOException;
import java.util.Optional;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.source.files.FileArea;
import se.sundsvall.aotloader.source.files.FileArea.LookupResult;
import se.sundsvall.aotloader.support.MigrationException;

/**
 * Finds and reads the files of an item in the file area of its source, and reports those that are not uploaded, for the
 * steps that upload files to the errand and to the post in PartyAssets.
 */
final class ItemFiles {

	private final AotLoaderProperties properties;

	ItemFiles(final AotLoaderProperties properties) {
		this.properties = properties;
	}

	/**
	 * Looks a file up in the file area of the candidate's source.
	 *
	 * @throws MigrationException when the file area cannot be read
	 */
	static FileArea.Lookup find(final FileArea fileArea, final FileReference reference, final Candidate candidate) {
		final var lookup = fileArea.find(reference);
		if (lookup.result() == LookupResult.DIRECTORY_UNAVAILABLE) {
			throw new MigrationException("Filarean för %s gick inte att läsa".formatted(candidate.key().source().getDisplayName()));
		}
		return lookup;
	}

	/**
	 * @return the content of the file, empty when it cannot be read or has grown larger than the largest size since it was
	 *         found, which the problems then list
	 */
	Optional<byte[]> read(final FileArea.Lookup lookup, final FileProblems problems) {
		try {
			final var content = FileArea.read(lookup.path(), properties.maxFileSize().toBytes());
			if (content.isEmpty()) {
				problems.add(FileProblem.TOO_LARGE, lookup.name());
			}
			return content;
		} catch (final IOException _) {
			problems.add(FileProblem.UNREADABLE, lookup.name());
			return Optional.empty();
		}
	}

	/**
	 * Lists the files that are not uploaded. A file that cannot be read, often only for a while, as when the share is busy
	 * or the file is open, fails the step, so that the next run uploads it.
	 *
	 * @throws MigrationException when a file could not be read
	 */
	void reportAndFailOnUnreadable(final FileProblems problems, final ReportNotes notes, final Candidate candidate) {
		problems.report(notes, maxFileSizeText());
		if (problems.has(FileProblem.UNREADABLE)) {
			throw new MigrationException("Filer på filarean för %s gick inte att läsa och laddas upp i nästa körning".formatted(candidate.key().source().getDisplayName()));
		}
	}

	/**
	 * @return the largest size of a file that is uploaded, e.g. "50 MB"
	 */
	String maxFileSizeText() {
		return properties.maxFileSize().toMegabytes() + " MB";
	}
}
