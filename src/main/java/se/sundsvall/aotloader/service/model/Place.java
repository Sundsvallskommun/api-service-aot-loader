package se.sundsvall.aotloader.service.model;

import java.util.List;

/**
 * The serving place or sales place, which becomes parameters of the errand, as pw-alkt has it (section 7.2.1).
 *
 * @param name       the name of the place
 * @param address    street address
 * @param zipCode    postal code
 * @param city       city
 * @param phone      phone number
 * @param email      email address
 * @param area       the name of the area the place is in, which decides the municipality of its errands (decision 19)
 * @param parameters the other parameters of the place, e.g. the restaurant number
 */
public record Place(String name, String address, String zipCode, String city, String phone, String email, String area, List<Parameter> parameters) {

	public Place {
		parameters = parameters == null ? List.of() : List.copyOf(parameters);
	}

	@Override
	public String toString() {
		return "Place[area=" + area + "]";
	}
}
