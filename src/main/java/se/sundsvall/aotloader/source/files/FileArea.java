package se.sundsvall.aotloader.source.files;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.FileAreaDirectories;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.FileReference.FileFolder;
import se.sundsvall.aotloader.support.Texts;

/**
 * The file area of one source, as an index of the file names in its flat directories (section 3.5). The index is built
 * once per run, the first time a directory is asked for. The file area is only read: the files are opened for reading
 * and nothing is ever written, moved or deleted there (K7). A file is only ever found through the index, never by a
 * path built from the name in the database.
 */
public final class FileArea {

	private static final String LINK_EXTENSION = ".url";
	// The largest array the JVM allocates
	private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

	private final Map<FileFolder, String> directories = new EnumMap<>(FileFolder.class);
	private final Map<FileFolder, DirectoryIndex> indexes = new EnumMap<>(FileFolder.class);
	private final long maxFileSize;

	public FileArea(final FileAreaDirectories directories, final long maxFileSize) {
		Optional.ofNullable(directories).ifPresent(dirs -> {
			Optional.ofNullable(Texts.trimToNull(dirs.documents())).ifPresent(dir -> this.directories.put(FileFolder.DOCUMENTS, dir));
			Optional.ofNullable(Texts.trimToNull(dirs.attachments())).ifPresent(dir -> this.directories.put(FileFolder.ATTACHMENTS, dir));
		});
		this.maxFileSize = maxFileSize;
	}

	/**
	 * Finds the file of a reference.
	 *
	 * @param  reference the reference in the database
	 * @return           what was found
	 */
	public synchronized Lookup find(final FileReference reference) {
		final var name = fileNameOf(reference.name());
		if (name == null) {
			return new Lookup(LookupResult.INVALID_NAME, reference.name(), null, 0);
		}
		if (name.toLowerCase(Locale.ROOT).endsWith(LINK_EXTENSION)) {
			return new Lookup(LookupResult.LINK, name, null, 0);
		}
		final var index = indexes.computeIfAbsent(reference.folder(), this::buildIndex);
		if (index.error() != null) {
			// Listed again at the next lookup, since the share may be back by then
			indexes.remove(reference.folder());
			return new Lookup(LookupResult.DIRECTORY_UNAVAILABLE, name, null, 0);
		}
		final var path = index.files().get(name.toLowerCase(Locale.ROOT));
		if (path == null) {
			return new Lookup(LookupResult.MISSING, name, null, 0);
		}
		try {
			final var size = Files.size(path);
			if (size > maxFileSize) {
				return new Lookup(LookupResult.TOO_LARGE, name, path, size);
			}
			return new Lookup(LookupResult.FOUND, path.getFileName().toString(), path, size);
		} catch (final IOException e) {
			return new Lookup(LookupResult.UNREADABLE, name, path, 0);
		}
	}

	/**
	 * Reads a file the index found, no more than the largest size a file may have. The file is opened for reading only.
	 *
	 * @param  maxSize the largest size a file may have, in bytes
	 * @return         the content, empty when the file has grown larger than that since it was found
	 */
	public static Optional<byte[]> read(final Path path, final long maxSize) throws IOException {
		try (var input = Files.newInputStream(path, StandardOpenOption.READ)) {
			final var content = input.readNBytes((int) (Math.min(MAX_ARRAY_SIZE - 1L, maxSize) + 1));
			return content.length > maxSize ? Optional.empty() : Optional.of(content);
		}
	}

	/**
	 * Checks that a directory of the file area exists and can be read.
	 *
	 * @return a description of the problem, or empty when the directory can be read
	 */
	public static Optional<String> checkDirectory(final String directory) {
		if (Texts.isBlank(directory)) {
			return Optional.of("katalogen är inte konfigurerad");
		}
		try {
			final var path = Path.of(directory);
			if (!Files.isDirectory(path) || !Files.isReadable(path)) {
				return Optional.of("katalogen finns inte eller går inte att läsa");
			}
			return Optional.empty();
		} catch (final InvalidPathException e) {
			return Optional.of("katalogens sökväg är ogiltig");
		}
	}

	/**
	 * The file name of a reference: the part after the last slash or backslash, should the database hold a path.
	 */
	static String fileNameOf(final String reference) {
		final var trimmed = Texts.trimToNull(reference);
		if (trimmed == null) {
			return null;
		}
		final var name = Texts.trimToNull(trimmed.substring(Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\')) + 1));
		return name == null || ".".equals(name) || "..".equals(name) ? null : name;
	}

	private DirectoryIndex buildIndex(final FileFolder folder) {
		final var directory = directories.get(folder);
		final var problem = checkDirectory(directory);
		if (problem.isPresent()) {
			return new DirectoryIndex(Map.of(), problem.get());
		}
		final var files = new HashMap<String, Path>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of(directory))) {
			for (final var path : stream) {
				if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
					files.putIfAbsent(path.getFileName().toString().toLowerCase(Locale.ROOT), path);
				}
			}
			return new DirectoryIndex(files, null);
		} catch (final IOException | RuntimeException e) {
			return new DirectoryIndex(Map.of(), "katalogen gick inte att lista: " + e.getClass().getSimpleName());
		}
	}

	/**
	 * What a lookup found.
	 */
	public enum LookupResult {
		FOUND,
		MISSING,
		TOO_LARGE,
		LINK,
		INVALID_NAME,
		UNREADABLE,
		DIRECTORY_UNAVAILABLE
	}

	/**
	 * The outcome of a lookup.
	 *
	 * @param result what was found
	 * @param name   the file name
	 * @param path   the file, when one was found
	 * @param size   the size of the file in bytes
	 */
	public record Lookup(LookupResult result, String name, Path path, long size) {
	}

	private record DirectoryIndex(Map<String, Path> files, String error) {
	}
}
