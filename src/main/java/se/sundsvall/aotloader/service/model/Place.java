package se.sundsvall.aotloader.service.model;

/**
 * The serving place or sales place, which becomes parameters of the errand, as pw-alkt has it (section 7.2.1).
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
 */
public record Place(String name, String address, String zipCode, String city, String phone, String email, String area, String restaurantNumber) {

	@Override
	public String toString() {
		return "Place[area=" + area + "]";
	}
}
