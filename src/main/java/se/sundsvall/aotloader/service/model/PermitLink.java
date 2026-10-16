package se.sundsvall.aotloader.service.model;

/**
 * What an inspection errand needs to be related to the permit errand of its place (section 7.7.1). The legal ids are
 * compared in memory only.
 *
 * @param permitKey        the permit of the place, null when the place has no current permit
 * @param permitHolder     the holder of that permit, the current owner of the place
 * @param inspectionHolder the holder the inspection errand concerned, null when it cannot be seen
 */
public record PermitLink(ItemKey permitKey, LegalId permitHolder, LegalId inspectionHolder) {

	/**
	 * @return true when the inspection errand concerned the holder of the current permit
	 */
	public boolean sameHolder() {
		return permitHolder != null && inspectionHolder != null && permitHolder.number().equals(inspectionHolder.number());
	}

	@Override
	public String toString() {
		return "PermitLink[permitKey=" + permitKey + "]";
	}
}
