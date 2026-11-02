package se.sundsvall.aotloader.source.alkt;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Splits a whole name as AlkT writes it, in one field, into the first name and the last name (decision 87). After a
 * comma the first name follows the last name. Otherwise the words tell the order, as analysis round 16 found it: the
 * last name comes first when the last word is a common first name and the first word is not, or when the first word is
 * a common last name or has the ending of one and neither word says otherwise. Any other name has its first name first,
 * the most common order: the last word is the last name and the words before it the first names. A prefix such as von
 * or af belongs to the last name, and a name of one word is the first name.
 */
final class PersonNames {

	private static final Locale SWEDISH = Locale.forLanguageTag("sv-SE");
	private static final Pattern BLANKS = Pattern.compile("\\s+");
	// The lists of analysis round 16, which found the order of the names of 970 serving staff with them
	private static final Set<String> FIRST_NAMES = setOf(
		"Agnes", "Agneta", "Aisha", "Alexandra", "Alice", "Alicia", "Alva", "Amanda", "Amelia", "Amira", "Angelica",
		"Angelika", "Anja", "Ann", "Anna", "Anne", "Anneli", "Annelie", "Anette", "Annette", "Annika", "Astrid", "Barbro",
		"Birgitta", "Britt", "Camilla", "Carina", "Caroline", "Catarina", "Cecilia", "Charlotta", "Charlotte", "Christina",
		"Cornelia", "Ebba", "Elin", "Elina", "Elisabet", "Elisabeth", "Elise", "Ella", "Ellen", "Elsa", "Elvira", "Emelie",
		"Emilia", "Emilie", "Emma", "Emmy", "Erika", "Ester", "Eva", "Evelina", "Fanny", "Fatima", "Felicia", "Frida",
		"Gun", "Gunilla", "Hanna", "Hannah", "Hedvig", "Helen", "Helena", "Hilda", "Ida", "Inga", "Ingela", "Inger",
		"Ingrid", "Isabella", "Isabelle", "Jasmin", "Jasmine", "Jeanette", "Jennifer", "Jenny", "Jessica", "Johanna",
		"Jonna", "Josefin", "Josefine", "Julia", "Kajsa", "Karin", "Karolina", "Katarina", "Kerstin", "Klara", "Kristina",
		"Leila", "Lena", "Lina", "Linda", "Linn", "Linnea", "Linnéa", "Lisa", "Lisbeth", "Liv", "Lotta", "Louise",
		"Lovisa", "Madeleine", "Maj", "Maja", "Malin", "Margareta", "Maria", "Marianne", "Marie", "Maryam", "Matilda",
		"Mia", "Michaela", "Mikaela", "Mira", "Moa", "Molly", "Mona", "Monica", "Monika", "My", "Natalie", "Nathalie",
		"Nellie", "Nina", "Nora", "Nour", "Olivia", "Paulina", "Pauline", "Petra", "Pia", "Rebecca", "Rebecka", "Ronja",
		"Saga", "Sandra", "Sanna", "Sara", "Sarah", "Selma", "Signe", "Siv", "Sofia", "Sofie", "Sophie", "Stina",
		"Susanne", "Thea", "Therese", "Terese", "Tilda", "Tina", "Tove", "Ulla", "Ulrika", "Vera", "Victoria", "Viktoria",
		"Wilma", "Yasmin", "Ylva", "Yvonne", "Zahra", "Åsa", "Adam", "Ahmed", "Albin", "Alex", "Alexander", "Alfred",
		"Ali", "Amir", "Anders", "Andreas", "Anton", "Arne", "Arvid", "August", "Axel", "Benjamin", "Bengt", "Bertil",
		"Björn", "Bo", "Carl", "Casper", "Christer", "Christian", "Christoffer", "Christopher", "Claes", "Conny", "Dan",
		"Daniel", "David", "Dennis", "Douglas", "Edvin", "Elias", "Elliot", "Emil", "Erik", "Felix", "Filip", "Fredrik",
		"Gabriel", "Gunnar", "Gustaf", "Gustav", "Göran", "Hampus", "Hannes", "Hans", "Harry", "Hassan", "Henrik", "Henry",
		"Herman", "Hugo", "Hussein", "Ibrahim", "Ingemar", "Isac", "Isak", "Ivar", "Jack", "Jacob", "Jakob", "Jan",
		"Jason", "Jens", "Jesper", "Jimmy", "Joakim", "Joel", "Johan", "Johannes", "John", "Johnny", "Jonas", "Jonatan",
		"Jonathan", "Josef", "Joseph", "Jörgen", "Kalle", "Karim", "Karl", "Kasper", "Kent", "Kevin", "Kim", "Kjell",
		"Klas", "Krister", "Kristian", "Kristoffer", "Lars", "Leif", "Lennart", "Leo", "Leon", "Liam", "Linus", "Love",
		"Lucas", "Ludvig", "Lukas", "Magnus", "Mahmoud", "Malte", "Marcus", "Markus", "Martin", "Mats", "Mattias",
		"Matthias", "Max", "Melker", "Melvin", "Michael", "Mikael", "Mohamed", "Mohammad", "Mohammed", "Muhammad",
		"Mustafa", "Måns", "Neo", "Niklas", "Nicklas", "Nils", "Noah", "Noel", "Ola", "Olle", "Olof", "Oliver", "Omar",
		"Oscar", "Oskar", "Otto", "Ove", "Patric", "Patrik", "Peter", "Petter", "Philip", "Pontus", "Per", "Pär", "Rasmus",
		"Robert", "Robin", "Roger", "Rolf", "Ronny", "Sam", "Samuel", "Sebastian", "Simon", "Sixten", "Stefan", "Stig",
		"Sven", "Theo", "Thomas", "Tobias", "Tomas", "Tommy", "Tony", "Torbjörn", "Ture", "Ulf", "Valter", "Viggo",
		"Viktor", "Victor", "Ville", "Vincent", "Walter", "William", "Wilmer", "Youssef", "Yusuf", "Åke");
	private static final Set<String> LAST_NAMES = setOf(
		"Berg", "Blom", "Björk", "Bodin", "Ek", "Ekman", "Engman", "Falk", "Hallin", "Hedin", "Hedman", "Hellman", "Holm",
		"Hägg", "Lind", "Lindh", "Lindman", "Lundin", "Lund", "Mellin", "Nordin", "Norin", "Nyman", "Sandin", "Sjödin",
		"Strand", "Ström", "Sundin", "Wallin", "Westin", "Wikman", "Öhman", "Bergman", "Kvist", "Rudin", "Edin", "Ahlin");
	private static final List<String> LAST_NAME_ENDINGS = List.of("son", "berg", "ström", "strom", "qvist", "kvist", "quist", "gren", "lund", "holm", "dahl", "blad", "stedt", "stad",
		"sjö", "fors", "bäck", "nen");
	private static final Set<String> PREFIXES = Set.of("von", "af", "de", "van", "der", "den", "du", "la", "le", "al", "el", "da", "di", "dos", "del");

	private PersonNames() {}

	/**
	 * The first name and the last name, either null when the name has none. It is personal data, and
	 * {@link #toString()} shows none of it.
	 */
	record Split(String firstName, String lastName) {

		@Override
		public String toString() {
			return "Split[firstName=" + (firstName != null) + ", lastName=" + (lastName != null) + "]";
		}
	}

	/**
	 * @param  name the whole name, stripped and with its blanks made one
	 * @return      the name split
	 */
	static Split split(final String name) {
		final var comma = name.indexOf(',');
		if (comma >= 0) {
			return new Split(words(name.substring(comma + 1)), words(name.substring(0, comma)));
		}
		final var words = name.split(" ");
		if (words.length == 1) {
			return new Split(name, null);
		}
		final var first = words[0];
		final var last = words[words.length - 1];
		final var lastNameFirst = (isFirstName(last) && !isFirstName(first)) || (isLastName(first) && !isFirstName(first) && !isLastName(last));
		if (lastNameFirst) {
			// The last name with the prefixes before it, and then the first names
			var end = 0;
			while (end < words.length - 1 && isPrefix(words[end])) {
				end++;
			}
			return new Split(join(words, end + 1, words.length), join(words, 0, end + 1));
		}
		// The first names, and then the last name with the prefixes before it
		var start = words.length - 1;
		while (start > 1 && isPrefix(words[start - 1])) {
			start--;
		}
		return new Split(join(words, 0, start), join(words, start, words.length));
	}

	/**
	 * @return true for a common first name, by the part before a hyphen
	 */
	private static boolean isFirstName(final String word) {
		return !isInitialOrPrefix(word) && FIRST_NAMES.contains(lowerCase(word.split("-", -1)[0]));
	}

	/**
	 * @return true for a common last name or one with the ending of one, by the part after a hyphen
	 */
	private static boolean isLastName(final String word) {
		if (isInitialOrPrefix(word)) {
			return false;
		}
		final var part = lowerCase(word.substring(word.lastIndexOf('-') + 1));
		return LAST_NAMES.contains(part) || LAST_NAME_ENDINGS.stream().anyMatch(part::endsWith);
	}

	private static boolean isInitialOrPrefix(final String word) {
		return word.replace(".", "").length() <= 1 || isPrefix(word);
	}

	private static boolean isPrefix(final String word) {
		return PREFIXES.contains(lowerCase(word));
	}

	private static String join(final String[] words, final int from, final int to) {
		return from >= to ? null : String.join(" ", Arrays.copyOfRange(words, from, to));
	}

	/**
	 * @return the words of the text, stripped and separated by one blank, null when it has none
	 */
	private static String words(final String text) {
		final var words = BLANKS.matcher(text.replace(',', ' ').strip()).replaceAll(" ");
		return words.isEmpty() ? null : words;
	}

	private static String lowerCase(final String word) {
		return word.toLowerCase(SWEDISH);
	}

	private static Set<String> setOf(final String... words) {
		return Stream.of(words).map(PersonNames::lowerCase).collect(Collectors.toUnmodifiableSet());
	}
}
