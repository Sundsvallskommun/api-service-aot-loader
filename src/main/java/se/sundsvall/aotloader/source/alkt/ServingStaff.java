package se.sundsvall.aotloader.source.alkt;

import java.text.Collator;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.StaffRow;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

/**
 * The serving staff of a place as the table the permit errand gets (decision 79): a row per person with the name and
 * the day the person became serving staff, in the order of the names. The personal identity number is not passed on,
 * so a name that occurs more than once on the place is one person, with the earliest day any of its rows has.
 */
final class ServingStaff {

	static final String KEY = "serveringsansvariga";
	static final String GROUP = "Serveringsansvariga i AlkT";
	static final List<String> COLUMNS = List.of("Namn", "Från");

	private static final Locale SWEDISH = Locale.forLanguageTag("sv-SE");
	// Puts å, ä and ö after z, as Swedish orders them
	private static final Collator COLLATOR = Collator.getInstance(SWEDISH);

	private ServingStaff() {}

	/**
	 * @param  staff the active serving staff of the place, in the order of the source
	 * @return       the rows of the table, each with the name and the day, the day null when no row of the person has one;
	 *               a row without a name is left out
	 */
	static List<List<String>> rows(final List<StaffRow> staff) {
		final var persons = new LinkedHashMap<String, Person>();
		staff.forEach(row -> Optional.ofNullable(Texts.trimToNull(row.namn()))
			.map(name -> name.replaceAll("\\s+", " "))
			.ifPresent(name -> persons.merge(name.toLowerCase(SWEDISH), new Person(name, row.fromDatum()), Person::earliest)));
		return persons.values().stream()
			.sorted(Comparator.comparing(Person::name, COLLATOR))
			.map(person -> Arrays.asList(person.name(), SourceValues.format(person.from())))
			.toList();
	}

	/**
	 * @param name the name as the first row of the person writes it
	 * @param from the earliest day of the rows of the person, null when none has one
	 */
	private record Person(String name, LocalDate from) {

		private Person earliest(final Person other) {
			return new Person(name, Stream.of(from, other.from).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null));
		}
	}
}
