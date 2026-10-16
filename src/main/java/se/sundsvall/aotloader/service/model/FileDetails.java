package se.sundsvall.aotloader.service.model;

import java.time.LocalDateTime;

/**
 * What the errand's attachment of a file gets besides the file itself (decision 40).
 *
 * @param purpose  the name of the attachment purpose of the namespace, e.g. REFERRAL_POLICE_REQUEST, null for none
 * @param received when the file came in, as the source dates it, null when the source has no date
 */
public record FileDetails(String purpose, LocalDateTime received) {

	/** Neither a purpose nor a date. */
	public static final FileDetails NONE = new FileDetails(null, null);

	/**
	 * @return these details, with what is missing taken from the other
	 */
	public FileDetails orElse(final FileDetails other) {
		if (other == null) {
			return this;
		}
		return new FileDetails(purpose == null ? other.purpose() : purpose, received == null ? other.received() : received);
	}
}
