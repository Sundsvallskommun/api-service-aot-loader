package se.sundsvall.aotloader.source.alkt;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.service.model.ServingPermitSubtype;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PermitRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ServingTime;
import se.sundsvall.aotloader.support.JsonFields;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;
import tools.jackson.databind.node.ObjectNode;

import static java.util.function.Predicate.not;
import static java.util.regex.Pattern.CANON_EQ;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.UNICODE_CASE;
import static java.util.regex.Pattern.UNICODE_CHARACTER_CLASS;
import static se.sundsvall.aotloader.service.model.ReviewRule.SERVING_TIME_UNPLACED;

/**
 * What a serving permit of AlkT was granted with, as Draken writes and reads it in the JSON of the decision of a permit
 * (decisions 76 and 92): the beverages, and the serving hours of the fields Draken has for the subtype of the permit.
 * <p>
 * AlkT keeps up to eight serving times, each with a text of its own that says what it is for, such as "Inomhus" or
 * "Uteservering" (analysis round 14). A permanent permit gets a time in each of Draken's four fields, indoors and
 * outdoors for the public and for closed company, that the texts point out: indoors or outdoors as the text says, the
 * first time indoors when its text says neither, and for the public or closed company as the text says, otherwise for
 * whom the permit serves. A text that names one of them goes before one that names neither. On a permit without outdoor
 * serving any other time whose text says neither is indoors too, but gives way to a time that its text or its order
 * places, as a time of a temporary addition may be longer than the one of the permit (decision 118). A temporary permit
 * and farm sales get their one time in Draken's one field. A time that cannot be placed, and a field that would get two
 * times, are left out of the decision and hit G34, since a wrong time would be worse than none; the times stay on the
 * errand and on the post in PartyAssets with their texts.
 */
final class DecidedScopes {

	static final String BEVERAGES = "beverages";
	static final String SERVING_HOURS = "servingHours";

	private static final int FLAGS = CASE_INSENSITIVE | UNICODE_CASE | UNICODE_CHARACTER_CLASS;
	private static final Pattern OUTDOORS = Pattern.compile("uteserv|utomhus|\\bute\\b|terr?ass|balkong|altan", FLAGS);
	private static final Pattern INDOORS = Pattern.compile("inomhus|\\binne\\b", FLAGS);
	// A room says indoors only in a text that says nothing of outdoors, as "Uteservering vid restaurangen" is outdoors
	private static final Pattern ROOMS = Pattern.compile("restaurang|lokal|matsal", FLAGS);
	private static final Pattern CLOSED_COMPANY = Pattern.compile("slutet sällskap|slutna sällskap", FLAGS | CANON_EQ);
	private static final Pattern PUBLIC = Pattern.compile("allmänhet", FLAGS | CANON_EQ);

	private DecidedScopes() {}

	/**
	 * A field of serving hours on a decision in Draken, in the order Draken shows them, with the audience and the place
	 * Draken knows it by in the JSON of the decision, both null for its one field, and its label.
	 */
	enum Slot {

		INDOOR_PUBLIC("PUBLIC", "INDOOR", "Serveringstid inne (allmänheten)"),
		OUTDOOR_PUBLIC("PUBLIC", "OUTDOOR", "Serveringstid ute (allmänheten)"),
		INDOOR_PRIVATE("CLOSED_COMPANY", "INDOOR", "Serveringstid inne (slutet sällskap)"),
		OUTDOOR_PRIVATE("CLOSED_COMPANY", "OUTDOOR", "Serveringstid ute (slutet sällskap)"),
		SINGLE(null, null, "Serveringstid");

		private final String audience;
		private final String location;
		private final String displayName;

		Slot(final String audience, final String location, final String displayName) {
			this.audience = audience;
			this.location = location;
			this.displayName = displayName;
		}

		String displayName() {
			return displayName;
		}

		JsonFields entry(final Range range) {
			return JsonFields.object()
				.text("audience", audience)
				.text("location", location)
				.text("from", range.from())
				.text("to", range.to());
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
	 * @return         what the decision decided
	 */
	static Decided of(final PermitRow permit, final ServingPermitSubtype subtype, final List<RuleHit> hits) {
		final var unplaced = new ArrayList<String>();
		final var intervals = intervals(permit);
		final Map<Slot, Range> placed = switch (subtype) {
			case PERMANENT_SERVING -> permanent(permit, intervals, unplaced);
			case TEMPORARY_SERVING_PUBLIC, TEMPORARY_SERVING_PRIVATE, FARM_SALES -> single(intervals, unplaced);
			// Draken has no field of serving hours for the others
			case PERMANENT_CATERING, TASTING -> Map.of();
			case null -> Map.of();
		};
		if (!unplaced.isEmpty()) {
			hits.add(RuleHit.of(SERVING_TIME_UNPLACED, String.join("; ", unplaced)));
		}
		final var servingHours = new EnumMap<Slot, Range>(Slot.class);
		servingHours.putAll(placed);
		return new Decided(ServingPermits.beverages(permit), servingHours.entrySet().stream().map(entry -> entry.getKey().entry(entry.getValue())).toList());
	}

	/**
	 * What the decision of a serving permit decided, as Draken writes it in the JSON of the decision.
	 *
	 * @param beverages    the beverages, as the constants of the alcohol schema, in its order
	 * @param servingHours the serving hours, an entry per field of Draken that got a time, in the order Draken shows them
	 */
	record Decided(List<String> beverages, List<JsonFields> servingHours) {

		Decided {
			beverages = List.copyOf(beverages);
			servingHours = List.copyOf(servingHours);
		}

		/**
		 * @param  permit the JSON of the permit, by the alcohol schema
		 * @return        the JSON of the permit with the beverages and the serving hours of the decision in place of those of
		 *                the permit, and without the field when the decision has none of them, so that Draken does not read
		 *                those of the permit as the decision's
		 */
		ObjectNode applyTo(final ObjectNode permit) {
			final var decided = JsonFields.object().texts(BEVERAGES, beverages).objects(SERVING_HOURS, servingHours).toNode();
			final var json = permit.deepCopy();
			for (final var field : List.of(BEVERAGES, SERVING_HOURS)) {
				if (decided.has(field)) {
					json.set(field, decided.get(field));
				} else {
					json.remove(field);
				}
			}
			return json;
		}

		@Override
		public String toString() {
			return "Decided[beverages=" + beverages + ", servingHours=" + servingHours.size() + "]";
		}
	}

	/**
	 * The serving hours of the permit as its post in PartyAssets has them (decision 87): an entry per time of AlkT, with
	 * the audience and the place only when its text names them alone, since a time without them holds for all serving,
	 * and with the text as its description. A time that cannot be read has its start and end in the description.
	 *
	 * @return the entries, in the order of AlkT
	 */
	static List<JsonFields> servingHours(final PermitRow permit) {
		return permit.servingTimes().stream()
			.filter(ServingTime::isSet)
			.map(DecidedScopes::servingHour)
			.toList();
	}

	private static JsonFields servingHour(final ServingTime time) {
		final var text = Optional.ofNullable(time.text()).orElse("");
		final var from = SourceValues.parseTime(time.start());
		final var to = SourceValues.parseTime(time.end());
		final var readable = from.isPresent() && to.isPresent();
		final var places = places(text, 0);
		final var closedCompany = CLOSED_COMPANY.matcher(text).find();
		final var entry = JsonFields.object();
		if (closedCompany != PUBLIC.matcher(text).find()) {
			entry.text("audience", closedCompany ? "CLOSED_COMPANY" : "PUBLIC");
		}
		if (places.size() == 1) {
			entry.text("location", Boolean.TRUE.equals(places.getFirst()) ? "OUTDOOR" : "INDOOR");
		}
		return entry
			.text("from", from.filter(_ -> readable).orElse(null))
			.text("to", to.filter(_ -> readable).orElse(null))
			.text("description", readable ? time.text() : Texts.joinWords(Texts.range(time.start(), time.end()), time.text()));
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
		intervals.forEach(interval -> place(permit, interval, candidates, unplaced));
		final var placed = new EnumMap<Slot, Range>(Slot.class);
		final var givenWay = new LinkedHashMap<Interval, List<Slot>>();
		candidates.forEach((slot, list) -> {
			final var named = list.stream().anyMatch(Candidate::named) ? list.stream().filter(Candidate::named).toList() : list;
			final var chosen = named.stream().allMatch(Candidate::implied) ? named : named.stream().filter(not(Candidate::implied)).toList();
			named.stream().filter(candidate -> !chosen.contains(candidate)).forEach(candidate -> givenWay.computeIfAbsent(candidate.interval(), _ -> new ArrayList<>()).add(slot));
			final var ranges = new LinkedHashSet<>(chosen.stream().map(candidate -> candidate.interval().range()).toList());
			if (ranges.size() == 1) {
				placed.put(slot, ranges.getFirst());
			} else {
				unplaced.add("%s: %s har olika tider".formatted(slot.displayName(), Texts.join(" och ", chosen.stream().map(candidate -> candidate.interval().toString()).toList())));
			}
		});
		givenWay.forEach((interval, slots) -> {
			if (slots.stream().noneMatch(slot -> interval.range().equals(placed.get(slot)))) {
				unplaced.add("%s: inne, eftersom tillståndet saknar uteservering, men %s har en annan tid".formatted(interval,
					Texts.join(" och ", slots.stream().map(Slot::displayName).toList())));
			}
		});
		return placed;
	}

	/**
	 * Adds the interval to the fields its text points out, or indoors when the text points out none and the permit has no
	 * outdoor serving (decision 118), or else to what cannot be placed.
	 */
	private static void place(final PermitRow permit, final Interval interval, final Map<Slot, List<Candidate>> candidates, final List<String> unplaced) {
		final var text = Optional.ofNullable(interval.text()).orElse("");
		final var stated = places(text, interval.number());
		final var implied = stated.isEmpty() && !permit.scope().uteservering();
		final var places = implied ? List.of(false) : stated;
		final var named = CLOSED_COMPANY.matcher(text).find() != PUBLIC.matcher(text).find();
		final var audiences = named ? List.of(CLOSED_COMPANY.matcher(text).find()) : audiences(permit);
		if (places.isEmpty()) {
			unplaced.add("%s: texten säger inte inne eller ute".formatted(interval));
		} else if (audiences.isEmpty()) {
			unplaced.add("%s: tillståndet anger inte om serveringen är för allmänheten eller slutet sällskap".formatted(interval));
		} else {
			places.forEach(outdoors -> audiences.forEach(closedCompany -> candidates.computeIfAbsent(Slot.of(outdoors, closedCompany), _ -> new ArrayList<>())
				.add(new Candidate(interval, named, implied))));
		}
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
	 * @param named   true when its text names the public or closed company, which goes before one that names neither
	 * @param implied true when it is indoors only because the permit has no outdoor serving, which gives way to one that
	 *                its text or its order places
	 */
	private record Candidate(Interval interval, boolean named, boolean implied) {
	}
}
