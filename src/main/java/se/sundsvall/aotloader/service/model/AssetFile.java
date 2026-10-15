package se.sundsvall.aotloader.service.model;

import static java.util.Objects.requireNonNull;

/**
 * A file that goes on the post in PartyAssets as well as on the errand.
 *
 * @param reference the file
 * @param category  what the file depicts, as the post shows it, e.g. Tillståndsbevis
 */
public record AssetFile(FileReference reference, String category) {

	public AssetFile {
		requireNonNull(reference, "reference");
		requireNonNull(category, "category");
	}
}
