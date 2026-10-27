package se.sundsvall.aotloader.service.model;

/**
 * The permit holder, or the party an inspection errand concerned. It is personal data for a sole trader: it is kept in
 * memory only, and {@link #toString()} shows none of it.
 *
 * @param legalId       the normalized number, null when the source has none that can be used
 * @param name          the name of the company or the sole trader
 * @param address       street address
 * @param zipCode       postal code
 * @param city          city
 * @param email         email address
 * @param phone         phone number
 * @param contactPerson the contact person
 */
public record Holder(LegalId legalId, String name, String address, String zipCode, String city, String email, String phone, String contactPerson) {

	@Override
	public String toString() {
		return "Holder[legalId=" + legalId + "]";
	}
}
