package se.sundsvall.aotloader.source;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.FileReference.FileFolder;
import se.sundsvall.aotloader.source.files.SharedFileNames;
import se.sundsvall.aotloader.support.Texts;

/**
 * Collects the file references of an item, each file once, in the order they are found, and marks the names that
 * another place of the source also refers to.
 */
public final class FileList {

	private final Map<String, FileReference> files = new LinkedHashMap<>();
	private final SharedFileNames sharedNames;

	public FileList(final SharedFileNames sharedNames) {
		this.sharedNames = sharedNames;
	}

	public FileList add(final String name, final FileFolder folder) {
		final var trimmed = Texts.trimToNull(name);
		if (trimmed != null) {
			files.putIfAbsent(folder + "/" + trimmed.toLowerCase(Locale.ROOT), new FileReference(trimmed, folder, sharedNames.isShared(trimmed, folder)));
		}
		return this;
	}

	public List<FileReference> build() {
		return List.copyOf(files.values());
	}
}
