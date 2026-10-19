package se.sundsvall.aotloader.service.model;

import java.time.LocalDateTime;

/**
 * What the errand's attachment of a file gets besides the file itself (decision 40).
 *
 * @param purpose       the name of the attachment purpose of the namespace, e.g. REFERRAL_POLICE_REQUEST, null for none
 * @param received      when the file came in, as the source dates it, null when the source has no date
 * @param investigation true when the file is material of the errand's investigation, which it is linked to (decision
 *                      60)
 */
public record FileDetails(String purpose, LocalDateTime received, boolean investigation) {

	/** Neither a purpose nor a date. */
	public static final FileDetails NONE = new FileDetails(null, null);

	/**
	 * Details of a file that is no material of the investigation.
	 */
	public FileDetails(final String purpose, final LocalDateTime received) {
		this(purpose, received, false);
	}

	/**
	 * @return these details, with what is missing taken from the other; a file that is material of the investigation in
	 *         either is so in both
	 */
	public FileDetails orElse(final FileDetails other) {
		if (other == null) {
			return this;
		}
		return new FileDetails(purpose == null ? other.purpose() : purpose, received == null ? other.received() : received, investigation || other.investigation());
	}
}
