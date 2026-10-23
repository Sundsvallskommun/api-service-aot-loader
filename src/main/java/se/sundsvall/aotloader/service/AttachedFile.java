package se.sundsvall.aotloader.service;

import generated.se.sundsvall.partyassets.AssetAttachment;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandAttachmentPurpose;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import se.sundsvall.aotloader.support.FileNames;

/**
 * A file on the errand or the post, or about to be uploaded to it.
 *
 * @param name      the file name
 * @param hash      the SHA-256 hash of the content, null when the target system did not give it
 * @param size      the size in bytes, null when the target system did not give it
 * @param id        the id of the attachment, null for a file not uploaded yet
 * @param purposeId the id of the attachment purpose, null for none
 * @param received  when the file came in, null when unknown
 */
record AttachedFile(String name, String hash, Integer size, String id, String purposeId, OffsetDateTime received) {

	AttachedFile(final String name, final String hash, final Integer size) {
		this(name, hash, size, null, null, null);
	}

	static AttachedFile of(final ErrandAttachment attachment) {
		return new AttachedFile(attachment.getFileName(), attachment.getHash(), attachment.getFileSize(), attachment.getId(),
			Optional.ofNullable(attachment.getPurpose()).map(ErrandAttachmentPurpose::getId).orElse(null), attachment.getReceived());
	}

	static AttachedFile of(final AssetAttachment attachment) {
		return new AttachedFile(attachment.getFileName(), null, attachment.getFileSize());
	}

	/**
	 * @return true for the same name, composed and regardless of case, and the same content: the same hash, or the same
	 *         size when a hash is missing. Without either, the files are taken to differ, since a file uploaded twice is
	 *         better than one that is never uploaded.
	 */
	boolean isSameAs(final AttachedFile other) {
		if (name == null || !FileNames.normalized(name).equals(FileNames.normalized(other.name()))) {
			return false;
		}
		if (hash != null && other.hash() != null) {
			return hash.equalsIgnoreCase(other.hash());
		}
		return size != null && Objects.equals(size, other.size());
	}
}
