package se.sundsvall.aotloader.source.alkt;

import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.StaffRow;
import se.sundsvall.aotloader.support.JsonFields;
import se.sundsvall.aotloader.support.LegalIds;
import se.sundsvall.aotloader.support.Texts;

/**
 * The serving staff of a place as the permit in PartyAssets has them (decision 87): a person per entry with the first
 * name, the last name, the date of birth and the day the person became serving staff, in the order of the names. Of the
 * personal identity number only the date of birth is passed on.
 * <p>
 * A person is told by the personal identity number, or by the name when the row has no whole number, and a person on
 * more than one row keeps the name of the first and the earliest day of any. AlkT has the whole name in one field,
 * which {@link PersonNames} splits.
 */
final class ServingStaff {

	static final String KEY = "responsibleServingStaff";

	private static final Locale SWEDISH = Locale.forLanguageTag("sv-SE");
	// Puts å, ä and ö after z, as Swedish orders them
	private static final Collator COLLATOR = Collator.getInstance(SWEDISH);
	private static final Pattern SPACES = Pattern.compile("\\s+");

	private ServingStaff() {}

	/**
	 * @param  staff the active serving staff of the place, in the order of the source
	 * @param  today the day the century of a date of birth without one is chosen from
	 * @return       the persons, each with what the source has of the four fields; a row without a name is left out
	 */
	static List<JsonFields> of(final List<StaffRow> staff, final LocalDate today) {
		final var byNumber = new LinkedHashMap<String, Person>();
		final var withoutNumber = new ArrayList<Person>();
		for (final var row : staff) {
			final var name = Optional.ofNullable(Texts.trimToNull(row.namn())).map(text -> SPACES.matcher(text).replaceAll(" ")).orElse(null);
			if (name == null) {
				continue;
			}
			final var person = new Person(name, LegalIds.birthDate(row.personnr(), today).orElse(null), row.fromDatum());
			wholeNumber(row.personnr()).ifPresentOrElse(number -> byNumber.merge(number, person, Person::with), () -> withoutNumber.add(person));
		}
		final var persons = new ArrayList<>(byNumber.values());
		for (final var person : withoutNumber) {
			final var known = IntStream.range(0, persons.size()).filter(index -> persons.get(index).sameName(person)).findFirst();
			if (known.isPresent()) {
				persons.set(known.getAsInt(), persons.get(known.getAsInt()).with(person));
			} else {
				persons.add(person);
			}
		}
		return persons.stream()
			.sorted(Comparator.comparing(Person::name, COLLATOR))
			.map(Person::toJson)
			.toList();
	}

	/**
	 * @return the last ten digits of a number with ten or twelve digits, which tell the person whether the number has its
	 *         century or not; empty for any other
	 */
	private static Optional<String> wholeNumber(final String personnr) {
		return LegalIds.digitsOf(personnr)
			.filter(digits -> digits.matches("\\d{10}|\\d{12}"))
			.map(digits -> digits.substring(digits.length() - 10));
	}

	/**
	 * @param name        the name as the first row of the person writes it
	 * @param dateOfBirth the date of birth, null when no row of the person has a number to read it from
	 * @param from        the earliest day of the rows of the person, null when none has one
	 */
	private record Person(String name, LocalDate dateOfBirth, LocalDate from) {

		private Person with(final Person other) {
			return new Person(name, Optional.ofNullable(dateOfBirth).orElse(other.dateOfBirth),
				Stream.of(from, other.from).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null));
		}

		private boolean sameName(final Person other) {
			return name.toLowerCase(SWEDISH).equals(other.name.toLowerCase(SWEDISH));
		}

		private JsonFields toJson() {
			final var split = PersonNames.split(name);
			return JsonFields.object()
				.text("firstName", split.firstName())
				.text("lastName", split.lastName())
				.date("dateOfBirth", dateOfBirth)
				.date("fromDate", from);
		}

		@Override
		public String toString() {
			return "Person[from=" + from + "]";
		}
	}
}
