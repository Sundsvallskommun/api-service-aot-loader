package se.sundsvall.aotloader.source;

/**
 * A row of Ägare or Sökande, which both sources have with the same columns. It is personal data for a sole trader, and
 * {@link #toString()} shows none of it.
 *
 * @param id              AgarID or SokandeID
 * @param organisationsNr the organization number, or the personal identity number of a sole trader, as written
 * @param namn            the name of the company or the sole trader
 * @param kontaktPerson   the contact person
 * @param adress          street address
 * @param adress2         second line of the street address
 * @param postnummer      postal code
 * @param postort         city
 * @param telefon         phone number
 * @param email           email address
 */
public record ContactRow(
	Integer id,
	String organisationsNr,
	String namn,
	String kontaktPerson,
	String adress,
	String adress2,
	String postnummer,
	String postort,
	String telefon,
	String email) {

	@Override
	public String toString() {
		return "ContactRow[id=" + id + "]";
	}
}
