package se.sundsvall.aotloader.source;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import se.sundsvall.aotloader.service.model.AssetFile;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.FileReference.FileFolder;
import se.sundsvall.aotloader.source.files.SharedFileNames;
import se.sundsvall.aotloader.support.Texts;

/**
 * Collects the file references of an item, each file once, in the order they are found, and marks the names that
 * another place of the source also refers to. A file can also be marked for the post in PartyAssets, in the category it
 * is first marked with.
 */
public final class FileList {

	private final Map<String, FileReference> files = new LinkedHashMap<>();
	private final Map<String, String> assetCategories = new LinkedHashMap<>();
	private final Set<String> errandOnly = new HashSet<>();
	private final SharedFileNames sharedNames;

	public FileList(final SharedFileNames sharedNames) {
		this.sharedNames = sharedNames;
	}

	public FileList add(final String name, final FileFolder folder) {
		final var trimmed = Texts.trimToNull(name);
		if (trimmed != null) {
			files.putIfAbsent(keyOf(trimmed, folder), new FileReference(trimmed, folder, sharedNames.isShared(trimmed, folder)));
		}
		return this;
	}

	/**
	 * Adds the file, which also goes on the post in PartyAssets in the category, or without one for null, unless an
	 * earlier call marked it for the post or kept it on the errand only.
	 */
	public FileList add(final String name, final FileFolder folder, final String assetCategory) {
		add(name, folder);
		final var trimmed = Texts.trimToNull(name);
		if (trimmed != null && !errandOnly.contains(keyOf(trimmed, folder)) && !assetCategories.containsKey(keyOf(trimmed, folder))) {
			assetCategories.put(keyOf(trimmed, folder), assetCategory);
		}
		return this;
	}

	/**
	 * Adds the file, which goes on the errand only, whatever a later call marks it with. A file an earlier call marked for
	 * the post stays marked.
	 */
	public FileList addToErrandOnly(final String name, final FileFolder folder) {
		add(name, folder);
		final var trimmed = Texts.trimToNull(name);
		if (trimmed != null && !assetCategories.containsKey(keyOf(trimmed, folder))) {
			errandOnly.add(keyOf(trimmed, folder));
		}
		return this;
	}

	public List<FileReference> build() {
		return List.copyOf(files.values());
	}

	/**
	 * @return the files that also go on the post in PartyAssets, in the order they were marked
	 */
	public List<AssetFile> assetFiles() {
		return assetCategories.entrySet().stream()
			.map(entry -> new AssetFile(files.get(entry.getKey()), entry.getValue()))
			.toList();
	}

	private static String keyOf(final String name, final FileFolder folder) {
		return folder + "/" + name.toLowerCase(Locale.ROOT);
	}
}
