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
	public static ContactRow toOwner(final ResultSet rs, final int rowNum) throws SQLException {
		return toContact(rs, "AgarID");
	}

	/**
	 * Reads a row of Sökande.
	 */
	public static ContactRow toApplicant(final ResultSet rs, final int rowNum) throws SQLException {
		return toContact(rs, "SokandeID");
	}

	/**
	 * Reads a row of PBI_ägare.
	 */
	public static PersonRow toPerson(final ResultSet rs, final int rowNum) throws SQLException {
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
	 * @return              the persons, without number when it is foreign or not a personal identity number
	 */
	public static List<Person> persons(final List<PersonRow> rows, final UnaryOperator<String> positionText, final LocalDate today) {
		return rows.stream()
			.map(person -> new Person(
				person.namn(),
				person.foreign() ? null : LegalIds.normalize(person.personnr(), today).filter(id -> id.type() == PRIVATE).orElse(null),
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
