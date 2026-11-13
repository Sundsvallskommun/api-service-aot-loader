package se.sundsvall.aotloader.service.model;

/**
 * The serving place or sales place, which the post in PartyAssets, the decision and the errand data of the errand name
 * (section 7.2.1).
 *
 * @param name             the name of the place
 * @param address          street address
 * @param zipCode          postal code
 * @param city             city
 * @param phone            phone number
 * @param email            email address
 * @param area             the name of the area the place is in, which decides the municipality of its errands
 *                         (decision 19)
 * @param restaurantNumber the restaurant number of a serving place, null for a sales place or when it has none
 * @param invoicing        where the invoices of the place go, null when the source has no invoice address for it
 */
public record Place(String name, String address, String zipCode, String city, String phone, String email, String area, String restaurantNumber, Invoicing invoicing) {

	/**
	 * A place without invoice details.
	 */
	public Place(final String name, final String address, final String zipCode, final String city, final String phone, final String email, final String area,
		final String restaurantNumber) {
		this(name, address, zipCode, city, phone, email, area, restaurantNumber, null);
	}

	@Override
	public String toString() {
		return "Place[area=" + area + "]";
	}
}
