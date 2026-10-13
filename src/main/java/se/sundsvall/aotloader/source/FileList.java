package se.sundsvall.aotloader.source;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.FileReference.FileFolder;
import se.sundsvall.aotloader.support.Texts;

/**
 * Collects the file references of an item, each file once, in the order they are found.
 */
public final class FileList {

	private final Map<String, FileReference> files = new LinkedHashMap<>();

	public FileList add(final String name, final FileFolder folder) {
		final var trimmed = Texts.trimToNull(name);
		if (trimmed != null) {
			files.putIfAbsent(folder + "/" + trimmed.toLowerCase(Locale.ROOT), new FileReference(trimmed, folder));
		}
		return this;
	}

	public List<FileReference> build() {
		return List.copyOf(files.values());
	}
}
