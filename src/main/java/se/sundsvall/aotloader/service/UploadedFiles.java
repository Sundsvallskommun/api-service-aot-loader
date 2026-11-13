package se.sundsvall.aotloader.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import se.sundsvall.aotloader.service.model.FileReference;

import static java.util.Collections.unmodifiableMap;

/**
 * The attachments the files of an item have on its errand, uploaded now or found there from an earlier run.
 *
 * @param attachmentIds the id of the attachment of each file that is on the errand; a file that is not, because it was
 *                      not found or was refused, is missing
 * @param material      the ids of the attachments that are material of the investigation (decision 60)
 */
record UploadedFiles(Map<FileReference, String> attachmentIds, Set<String> material) {

	/** No files on the errand. */
	static final UploadedFiles NONE = new UploadedFiles(Map.of(), Set.of());

	UploadedFiles {
		attachmentIds = attachmentIds == null ? Map.of() : unmodifiableMap(new LinkedHashMap<>(attachmentIds));
		material = material == null ? Set.of() : Set.copyOf(material);
	}

	/**
	 * @return the ids of the attachments of the files, in their order, without the files that are not on the errand
	 */
	List<String> idsOf(final Collection<FileReference> files) {
		return files.stream().map(attachmentIds::get).filter(Objects::nonNull).distinct().toList();
	}
}
