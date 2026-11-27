package se.sundsvall.aotloader.service.model;

/**
 * Where the invoices of the place go, as the source has it: the recipient, its address and the person the invoice is
 * marked for. It may name a person, and {@link #toString()} shows none of it.
 *
 * @param name          the recipient of the invoices
 * @param address       street address
 * @param zipCode       postal code
 * @param city          city
 * @param contactPerson the person the invoices are marked for
 */
public record Invoicing(String name, String address, String zipCode, String city, String contactPerson) {

	@Override
	public String toString() {
		return "Invoicing[]";
	}
}
