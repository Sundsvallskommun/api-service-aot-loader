package se.sundsvall.aotloader.source.alkt;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.service.model.ServingPermitSubtype;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.Drinks;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PermitRow;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.UNICODE_CASE;
import static java.util.regex.Pattern.UNICODE_CHARACTER_CLASS;
import static se.sundsvall.aotloader.Constants.DECISION_PARAMETER_GROUP_DECIDED;
import static se.sundsvall.aotloader.service.model.ReviewRule.SERVING_TIME_UNPLACED;

/**
 * What a serving permit of AlkT was granted with, as the parameters Draken writes and reads on the decision of a permit
 * (decision 76): the beverages, and the serving hours of the fields Draken has for the subtype of the permit.
 * <p>
 * AlkT keeps up to eight serving times, each with a text of its own that says what it is for, such as "Inomhus" or
 * "Uteservering" (analysis round 14). A permanent permit gets a time in each of Draken's four fields, indoors and
 * outdoors for the public and for closed company, that the texts point out: indoors or outdoors as the text says, the
 * first time indoors when its text says neither, and for the public or closed company as the text says, otherwise for
 * whom the permit serves. A text that names one of them goes before one that names neither. A temporary permit and farm
 * sales get their one time in Draken's one field. A time that cannot be placed, and a field that would get two
 * different
 * times, are left out of the decision and hit G34, since a wrong time would be worse than none; the times stay on the
 * errand with their texts.
 */
final class DecidedScopes {

	static final String BEVERAGES = "decidedBeverages";
	private static final String BEVERAGES_DISPLAY_NAME = "Alkoholdrycker som får serveras";

	private static final int FLAGS = CASE_INSENSITIVE | UNICODE_CASE | UNICODE_CHARACTER_CLASS;
	private static final Pattern OUTDOORS = Pattern.compile("uteserv|utomhus|\\bute\\b|terr?ass|balkong|altan", FLAGS);
	private static final Pattern INDOORS = Pattern.compile("inomhus|\\binne\\b", FLAGS);
	// A room says indoors only in a text that says nothing of outdoors, as "Uteservering vid restaurangen" is outdoors
	private static final Pattern ROOMS = Pattern.compile("restaurang|lokal|matsal", FLAGS);
	private static final Pattern CLOSED_COMPANY = Pattern.compile("slutet sällskap|slutna sällskap", FLAGS);
	private static final Pattern PUBLIC = Pattern.compile("allmänhet", FLAGS);

	private DecidedScopes() {}

	/**
	 * A field of serving hours on a decision in Draken, with its key and the label Draken gives it.
	 */
	enum Slot {

		INDOOR_PUBLIC("decidedServingHoursIndoorPublic", "Serveringstid inne (allmänheten)"),
		OUTDOOR_PUBLIC("decidedServingHoursOutdoorPublic", "Serveringstid ute (allmänheten)"),
		INDOOR_PRIVATE("decidedServingHoursIndoorPrivate", "Serveringstid inne (slutet sällskap)"),
		OUTDOOR_PRIVATE("decidedServingHoursOutdoorPrivate", "Serveringstid ute (slutet sällskap)"),
		SINGLE("decidedServingHours", "Serveringstid");

		private final String key;
		private final String displayName;

		Slot(final String key, final String displayName) {
			this.key = key;
			this.displayName = displayName;
		}

		String key() {
			return key;
		}

		String displayName() {
			return displayName;
		}

		static Slot of(final boolean outdoors, final boolean closedCompany) {
			if (outdoors) {
				return closedCompany ? OUTDOOR_PRIVATE : OUTDOOR_PUBLIC;
			}
			return closedCompany ? INDOOR_PRIVATE : INDOOR_PUBLIC;
		}
	}

	/**
	 * @param  permit  the permit
	 * @param  subtype its subtype, null when it has none
	 * @param  hits    the rule hits of the permit, to which G34 is added for the times left out
	 * @return         the parameters of the decision, in the order Draken shows them; none for a permit without beverages
	 *                 and without serving hours Draken has a field for
	 */
	static List<Parameter> of(final PermitRow permit, final ServingPermitSubtype subtype, final List<RuleHit> hits) {
		final var parameters = new ArrayList<Parameter>();
		beverages(permit.drinks()).ifPresent(parameters::add);
		final var unplaced = new ArrayList<String>();
		final var intervals = intervals(permit);
		final Map<Slot, Range> placed = switch (subtype) {
			case PERMANENT_SERVING -> permanent(permit, intervals, unplaced);
			case TEMPORARY_SERVING_PUBLIC, TEMPORARY_SERVING_PRIVATE, FARM_SALES -> single(intervals, unplaced);
			// Draken has no field of serving hours for the others
			case PERMANENT_CATERING, TASTING -> Map.of();
			case null -> Map.of();
		};
		placed.forEach((slot, range) -> parameters.add(new Parameter(slot.key(), slot.displayName(), DECISION_PARAMETER_GROUP_DECIDED, List.of(range.from(), range.to()))));
		if (!unplaced.isEmpty()) {
			hits.add(RuleHit.of(SERVING_TIME_UNPLACED, String.join("; ", unplaced)));
		}
		return parameters;
	}

	/**
	 * @return the beverages, as the constants Draken knows them by; empty when the permit has none of them. Alcohol-like
	 *         preparations, ALP, have none.
	 */
	private static Optional<Parameter> beverages(final Drinks drinks) {
		final var values = new ArrayList<String>();
		if (drinks.starkol()) {
			values.add("STARKOL");
		}
		if (drinks.vin()) {
			values.add("VIN");
		}
		if (drinks.spritdrycker()) {
			values.add("SPRITDRYCKER");
		}
		if (drinks.ajaDrycker()) {
			values.add("CIDER_ELLER_ANDRA_JASTA_ALKOHOLDRYCKER");
		}
		return values.isEmpty() ? Optional.empty() : Optional.of(new Parameter(BEVERAGES, BEVERAGES_DISPLAY_NAME, DECISION_PARAMETER_GROUP_DECIDED, values));
	}

	/**
	 * @return the intervals that have a start and an end that can be read; one that cannot hits G9 elsewhere
	 */
	private static List<Interval> intervals(final PermitRow permit) {
		final var intervals = new ArrayList<Interval>();
		for (var i = 0; i < permit.servingTimes().size(); i++) {
			final var time = permit.servingTimes().get(i);
			final var from = SourceValues.parseTime(time.start());
			final var to = SourceValues.parseTime(time.end());
			if (from.isPresent() && to.isPresent()) {
				intervals.add(new Interval(i + 1, new Range(clock(from.get()), clock(to.get())), time.text()));
			}
		}
		return intervals;
	}

	private static Map<Slot, Range> permanent(final PermitRow permit, final List<Interval> intervals, final List<String> unplaced) {
		final var candidates = new EnumMap<Slot, List<Candidate>>(Slot.class);
		for (final var interval : intervals) {
			final var text = Optional.ofNullable(interval.text()).orElse("");
			final var places = places(text, interval.number());
			if (places.isEmpty()) {
				unplaced.add("%s: texten säger inte inne eller ute".formatted(interval));
				continue;
			}
			final var named = CLOSED_COMPANY.matcher(text).find() != PUBLIC.matcher(text).find();
			final var audiences = named ? List.of(CLOSED_COMPANY.matcher(text).find()) : audiences(permit);
			if (audiences.isEmpty()) {
				unplaced.add("%s: tillståndet anger inte om serveringen är för allmänheten eller slutet sällskap".formatted(interval));
				continue;
			}
			places.forEach(outdoors -> audiences.forEach(closedCompany -> candidates.computeIfAbsent(Slot.of(outdoors, closedCompany), _ -> new ArrayList<>())
				.add(new Candidate(interval, named))));
		}
		final var placed = new EnumMap<Slot, Range>(Slot.class);
		candidates.forEach((slot, list) -> {
			final var chosen = list.stream().anyMatch(Candidate::named) ? list.stream().filter(Candidate::named).toList() : list;
			final var ranges = new LinkedHashSet<>(chosen.stream().map(candidate -> candidate.interval().range()).toList());
			if (ranges.size() == 1) {
				placed.put(slot, ranges.getFirst());
			} else {
				unplaced.add("%s: %s har olika tider".formatted(slot.displayName(), Texts.join(" och ", chosen.stream().map(candidate -> candidate.interval().toString()).toList())));
			}
		});
		return placed;
	}

	/**
	 * @return where the text says the time is for, false for indoors and true for outdoors, both when it says both; a room
	 *         such as a restaurant is indoors only when the text says nothing of outdoors, the first time is indoors when
	 *         the text says neither, and another one is placed nowhere
	 */
	private static List<Boolean> places(final String text, final int number) {
		final var places = new ArrayList<Boolean>();
		final var outdoors = OUTDOORS.matcher(text).find();
		if (INDOORS.matcher(text).find() || (!outdoors && ROOMS.matcher(text).find())) {
			places.add(false);
		}
		if (outdoors) {
			places.add(true);
		}
		if (places.isEmpty() && number == 1) {
			places.add(false);
		}
		return places;
	}

	/**
	 * @return whom the permit serves, false for the public and true for closed company
	 */
	private static List<Boolean> audiences(final PermitRow permit) {
		final var audiences = new ArrayList<Boolean>();
		if (permit.servingPublic()) {
			audiences.add(false);
		}
		if (permit.servingPrivate()) {
			audiences.add(true);
		}
		return audiences;
	}

	private static Map<Slot, Range> single(final List<Interval> intervals, final List<String> unplaced) {
		final var ranges = new LinkedHashSet<>(intervals.stream().map(Interval::range).toList());
		if (ranges.size() > 1) {
			unplaced.add("tillståndet har %d olika serveringstider, och Draken har ett fält för den här sortens tillstånd".formatted(ranges.size()));
			return Map.of();
		}
		return ranges.isEmpty() ? Map.of() : Map.of(Slot.SINGLE, ranges.getFirst());
	}

	/**
	 * @return the time as Draken takes it: 24:00 as 00:00, the same moment
	 */
	private static String clock(final String time) {
		return "24:00".equals(time) ? "00:00" : time;
	}

	/**
	 * A serving time interval that can be read.
	 *
	 * @param number its number in AlkT, 1-8
	 * @param range  its start and end
	 * @param text   what it is for, null when blank
	 */
	private record Interval(int number, Range range, String text) {

		@Override
		public String toString() {
			return "serveringstid %d (%s–%s)".formatted(number, range.from(), range.to());
		}
	}

	private record Range(String from, String to) {
	}

	/**
	 * An interval that points to a field.
	 *
	 * @param named true when its text names the public or closed company, which goes before one that names neither
	 */
	private record Candidate(Interval interval, boolean named) {
	}
}
