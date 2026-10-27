package se.sundsvall.aotloader.source;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.Person;
import se.sundsvall.aotloader.support.LegalIds;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.service.model.PartyType.PRIVATE;
import static se.sundsvall.aotloader.source.Rows.flag;
import static se.sundsvall.aotloader.source.Rows.integer;
import static se.sundsvall.aotloader.source.Rows.text;

/**
 * Reads the owners, applicants and persons with significant influence, which AlkT and AlkOL store alike, and turns them
 * into the holder and the persons of a candidate.
 */
public final class PartyRows {

	private PartyRows() {}

	/**
	 * Reads a row of Ägare.
	 */
	public static ContactRow toOwner(final ResultSet rs) throws SQLException {
		return toContact(rs, "AgarID");
	}

	/**
	 * Reads a row of Sökande.
	 */
	public static ContactRow toApplicant(final ResultSet rs) throws SQLException {
		return toContact(rs, "SokandeID");
	}

	/**
	 * Reads a row of PBI_ägare.
	 */
	public static PersonRow toPerson(final ResultSet rs) throws SQLException {
		return new PersonRow(
			integer(rs, "AgarID"),
			text(rs, "Namn"),
			text(rs, "Personnr"),
			Stream.of(text(rs, "Befattningskod1"), text(rs, "Befattningskod2"), text(rs, "Befattningskod3")).filter(Objects::nonNull).toList(),
			flag(rs, "UtlandskPerson"));
	}

	/**
	 * @return the holder with the contact's normalized number, null without contact
	 */
	public static Holder holder(final ContactRow contact, final LocalDate today) {
		if (contact == null) {
			return null;
		}
		return new Holder(LegalIds.normalize(contact.organisationsNr(), today).orElse(null), contact.namn(), Texts.joinWords(contact.adress(), contact.adress2()),
			contact.postnummer(), contact.postort(), contact.email(), contact.telefon(), contact.kontaktPerson());
	}

	/**
	 * @param  rows         the persons of an owner
	 * @param  positionText the text of a position code
	 * @param  today        the date the century of a personal identity number is chosen from
	 * @return              the persons, without number when it cannot be normalized; an organization number, e.g. of an
	 *                      owning company, is kept, while a person the source marks as foreign keeps only a Swedish
	 *                      personal identity number with a valid check digit
	 */
	public static List<Person> persons(final List<PersonRow> rows, final UnaryOperator<String> positionText, final LocalDate today) {
		return rows.stream()
			.map(person -> new Person(
				person.namn(),
				LegalIds.normalize(person.personnr(), today)
					.filter(id -> !person.foreign() || (id.type() == PRIVATE && LegalIds.hasValidCheckDigit(id)))
					.orElse(null),
				person.foreign(),
				person.positionCodes().stream().map(positionText).toList()))
			.toList();
	}

	/**
	 * @return a key that is the same for one number however it is written: the normalized number, or the digits when the
	 *         number cannot be normalized; empty for no number
	 */
	public static Optional<String> numberKey(final String raw, final LocalDate today) {
		return LegalIds.normalize(raw, today).map(LegalId::number).or(() -> LegalIds.digitsOf(raw));
	}

	/**
	 * @return true when both have a number that can be read and it is the same, so that two posts of one holder are the
	 *         same holder
	 */
	public static boolean sameHolder(final ContactRow one, final ContactRow other, final LocalDate today) {
		final var number = numberOf(one, today);
		return number.isPresent() && number.equals(numberOf(other, today));
	}

	/**
	 * @return true when both have a number that can be normalized and the numbers differ; false when either has none or
	 *         one that cannot be normalized, which may be a number of the same holder written in part, so that the holder
	 *         is unknown (section 7.7.1)
	 */
	public static boolean otherHolder(final ContactRow one, final ContactRow other, final LocalDate today) {
		final var number = normalizedNumberOf(one, today);
		final var otherNumber = normalizedNumberOf(other, today);
		return number.isPresent() && otherNumber.isPresent() && !number.equals(otherNumber);
	}

	/**
	 * For the rules that keep what may be another holder's from the current one, where a number that cannot be normalized
	 * is no sign of the same holder: G19, which has the item reviewed, and G31, which keeps the files on the errand.
	 *
	 * @return true when both have a number and the numbers differ, compared as {@link #numberKey} gives them; false when
	 *         either has none
	 */
	public static boolean mayBeOtherHolder(final ContactRow one, final ContactRow other, final LocalDate today) {
		final var number = numberOf(one, today);
		final var otherNumber = numberOf(other, today);
		return number.isPresent() && otherNumber.isPresent() && !number.equals(otherNumber);
	}

	private static Optional<String> numberOf(final ContactRow contact, final LocalDate today) {
		return Optional.ofNullable(contact).flatMap(found -> numberKey(found.organisationsNr(), today));
	}

	private static Optional<String> normalizedNumberOf(final ContactRow contact, final LocalDate today) {
		return Optional.ofNullable(contact).flatMap(found -> LegalIds.normalize(found.organisationsNr(), today)).map(LegalId::number);
	}

	private static ContactRow toContact(final ResultSet rs, final String idColumn) throws SQLException {
		return new ContactRow(
			integer(rs, idColumn),
			text(rs, "OrganisationsNr"),
			text(rs, "Bolagsnamn"),
			text(rs, "KontaktPerson"),
			text(rs, "GAdress"),
			text(rs, "GAdress2"),
			text(rs, "PNr"),
			text(rs, "POrt"),
			text(rs, "TelefonNr1"),
			text(rs, "EMail"));
	}
}
