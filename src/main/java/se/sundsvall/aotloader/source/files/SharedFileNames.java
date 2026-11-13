package se.sundsvall.aotloader.source.files;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.FileAreaDirectories;
import se.sundsvall.aotloader.service.model.FileReference.FileFolder;
import se.sundsvall.aotloader.support.FileNames;
import se.sundsvall.aotloader.support.Texts;

import static java.util.Objects.requireNonNull;

/**
 * The file names that more than one place of a source refers to, by directory of the file area. The directories are
 * flat, so places that refer to the same name share one file, which belongs to at most one of them. Such a file is not
 * migrated (G14). Names are compared the way the file area finds them: the last part of a path, composed and regardless
 * of case.
 * When a source keeps its documents and its attachments in the same directory, a name is one file in both.
 */
public final class SharedFileNames {

	private final Set<String> shared;
	private final boolean oneDirectory;

	private SharedFileNames(final Set<String> shared, final boolean oneDirectory) {
		this.shared = shared;
		this.oneDirectory = oneDirectory;
	}

	/**
	 * @param  directories the directories of the source's file area
	 * @return             a builder for the source's file references
	 */
	public static Builder builder(final FileAreaDirectories directories) {
		return new Builder(isOneDirectory(directories));
	}

	/**
	 * @return true when another place refers to the same name in the same directory
	 */
	public boolean isShared(final String name, final FileFolder folder) {
		final var key = key(name, folder, oneDirectory);
		return key != null && shared.contains(key);
	}

	private static String key(final String name, final FileFolder folder, final boolean oneDirectory) {
		final var fileName = FileNames.key(name);
		if (fileName == null || folder == null) {
			return null;
		}
		return (oneDirectory ? "" : folder + "/") + fileName;
	}

	private static boolean isOneDirectory(final FileAreaDirectories directories) {
		final var documents = directories == null ? null : Texts.trimToNull(directories.documents());
		final var attachments = directories == null ? null : Texts.trimToNull(directories.attachments());
		if (documents == null || attachments == null) {
			return false;
		}
		try {
			return Path.of(documents).toAbsolutePath().normalize().equals(Path.of(attachments).toAbsolutePath().normalize());
		} catch (final InvalidPathException _) {
			return documents.equalsIgnoreCase(attachments);
		}
	}

	/**
	 * Collects every file reference of a source with the place it belongs to.
	 */
	public static final class Builder {

		private final Map<String, String> placeOfName = new HashMap<>();
		private final Set<String> shared = new HashSet<>();
		private final boolean oneDirectory;

		private Builder(final boolean oneDirectory) {
			this.oneDirectory = oneDirectory;
		}

		/**
		 * @param  place  the place the reference belongs to, e.g. "objekt:12"; an errand without a place is a place of
		 *                its own
		 * @param  name   the file name as the database has it; a blank name is ignored
		 * @param  folder the directory of the file area
		 * @return        this builder
		 */
		public Builder add(final String place, final String name, final FileFolder folder) {
			requireNonNull(place, "place");
			final var key = key(name, folder, oneDirectory);
			if (key != null) {
				final var first = placeOfName.putIfAbsent(key, place);
				if (first != null && !first.equals(place)) {
					shared.add(key);
				}
			}
			return this;
		}

		public SharedFileNames build() {
			return new SharedFileNames(Set.copyOf(shared), oneDirectory);
		}
	}
}
