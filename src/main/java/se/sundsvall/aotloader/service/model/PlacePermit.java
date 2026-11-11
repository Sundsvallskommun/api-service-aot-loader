package se.sundsvall.aotloader.service.model;

import java.time.LocalDate;

/**
 * The current permit of a place, which an inspection of the place is related to when it concerned the same holder
 * (decision 61).
 *
 * @param key           the permit
 * @param issued        the day the permit was issued, null when unknown
 * @param ended         the last day of the permit, as the source tells it, null when the source tells none
 * @param issuedToOwner false when the permit may have been issued to another than the current owner of the place, who
 *                      is its holder (G2, G19), or when the place has several permits
 * @param history       the earlier permits and owners of the place, which show whether an inspection before the day the
 *                      permit was issued concerned its holder (decision 114); null when the source has none
 */
public record PlacePermit(ItemKey key, LocalDate issued, LocalDate ended, boolean issuedToOwner, HolderHistory history) {

	/**
	 * A permit of a place whose source has no history of its holders.
	 */
	public PlacePermit(final ItemKey key, final LocalDate issued, final LocalDate ended, final boolean issuedToOwner) {
		this(key, issued, ended, issuedToOwner, null);
	}
}
