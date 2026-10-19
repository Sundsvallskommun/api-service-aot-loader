package se.sundsvall.aotloader.service.model;

import java.time.LocalDate;

/**
 * What an inspection errand or visit needs to be related to the permit errand of its place (section 7.7.1, decision
 * 61). The legal ids are compared in memory only.
 *
 * @param permit           the current permit of the place, null when the place has none
 * @param permitHolder     the holder of that permit, the current owner of the place; null when it cannot be seen or for
 *                         a visit, which is related by its day
 * @param inspectionHolder the holder the inspection errand concerned, null when it cannot be seen, as for every visit
 * @param inspected        the day of the inspection: of the visit, or the day the errand was opened or else registered;
 *                         null when unknown
 */
public record PermitLink(PlacePermit permit, LegalId permitHolder, LegalId inspectionHolder, LocalDate inspected) {

	/**
	 * Why an inspection is related to the permit or not.
	 */
	public enum Basis {

		/** The place has no current permit. */
		NO_PERMIT(false),
		/** The inspection concerned the holder of the permit. */
		SAME_HOLDER(true),
		/** The inspection concerned another holder. */
		OTHER_HOLDER(false),
		/** The inspection's holder is known, but not the permit's. */
		NO_PERMIT_HOLDER(false),
		/** Neither the holder nor the day of the inspection is known. */
		NO_DATE(false),
		/** The day the permit was issued is unknown. */
		NO_ISSUE_DATE(false),
		/**
		 * The permit may have been issued to another than the current owner, or the place has several, so its day tells
		 * nothing.
		 */
		ISSUED_TO_OTHER(false),
		/** The inspection was before the permit was issued. */
		BEFORE_ISSUE(false),
		/** The inspection was after the permit ended. */
		AFTER_END(false),
		/** The inspection was on or after the day the permit was issued, while it held, so it concerned its holder. */
		AFTER_ISSUE(true);

		private final boolean relates;

		Basis(final boolean relates) {
			this.relates = relates;
		}

		public boolean relates() {
			return relates;
		}
	}

	/**
	 * @return the permit, null when the place has none
	 */
	public ItemKey permitKey() {
		return permit == null ? null : permit.key();
	}

	/**
	 * Decides whether the inspection concerned the holder of the permit. Its holder decides when it is known, whatever its
	 * day. Otherwise its day does: a new holder needs a new permit, so an inspection on or after the day the permit was
	 * issued, and not after it ended, concerned the holder of the permit, unless the permit may have been issued to
	 * another than the current owner.
	 *
	 * @return the basis, which tells whether there is a relation
	 */
	public Basis basis() {
		if (permit == null) {
			return Basis.NO_PERMIT;
		}
		if (inspectionHolder != null) {
			if (permitHolder == null) {
				return Basis.NO_PERMIT_HOLDER;
			}
			return permitHolder.number().equals(inspectionHolder.number()) ? Basis.SAME_HOLDER : Basis.OTHER_HOLDER;
		}
		if (inspected == null) {
			return Basis.NO_DATE;
		}
		if (!permit.issuedToOwner()) {
			return Basis.ISSUED_TO_OTHER;
		}
		if (permit.issued() == null) {
			return Basis.NO_ISSUE_DATE;
		}
		if (inspected.isBefore(permit.issued())) {
			return Basis.BEFORE_ISSUE;
		}
		if (permit.ended() != null && inspected.isAfter(permit.ended())) {
			return Basis.AFTER_END;
		}
		return Basis.AFTER_ISSUE;
	}

	@Override
	public String toString() {
		return "PermitLink[permitKey=" + permitKey() + "]";
	}
}
