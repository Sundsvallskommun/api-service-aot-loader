package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.ErrandTypeSettings;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.InvestigationData;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.source.InspectionVisitRows.RemarkRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitRow;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.service.model.Kind.TILLSYNSBESOK;
import static se.sundsvall.aotloader.service.model.ReviewRule.PLACE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.VISIT_DATE_INVALID;
import static se.sundsvall.aotloader.source.CandidateTexts.line;
import static se.sundsvall.aotloader.source.CandidateTexts.migrated;

/**
 * Builds the candidates of the inspection visits of a source, the same way for both sources (section 7.7.2): which
 * visits are migrated, and their rules, title, description, labels and parameters. A visit gets no holder, since the
 * source does not tell which holder the place had at the time, and so no relation to the permit errand either.
 */
public final class InspectionVisitCandidates {

	private static final Pattern SECONDS = Pattern.compile("^(\\d{1,2}[:.]\\d{2})[:.]\\d{2}$");

	private final ErrandType type;
	private final ErrandTypeSettings settings;
	private final String unknownPlace;
	private final String placeMissing;
	private final LocalDate today;

	/**
	 * @param type         the type of errand the visits of the source become
	 * @param properties   the settings of the migration
	 * @param unknownPlace the name in the title of a visit without a place
	 * @param placeMissing the detail of rule G1 for a visit without a place
	 * @param today        the day of the migration
	 */
	public InspectionVisitCandidates(final ErrandType type, final AotLoaderProperties properties, final String unknownPlace, final String placeMissing, final LocalDate today) {
		this.type = type;
		this.settings = properties.errandType(type);
		this.unknownPlace = unknownPlace;
		this.placeMissing = placeMissing;
		this.today = today;
	}

	/**
	 * @param  placeHasPermit true when the place of the visit has a current permit
	 * @return                true when the visit is to be migrated: its place has a current permit when the settings take
	 *                        only those
	 */
	public boolean selects(final boolean placeHasPermit) {
		return placeHasPermit || !settings.onlyPlacesWithPermit();
	}

	/**
	 * Starts the candidate of a visit with what both sources give it. The source adds the rest, such as the files.
	 *
	 * @param  visit the visit
	 * @param  rows  the visits of the source, with the remarks and the code lists
	 * @param  place the place of the visit, null when it has none
	 * @return       the builder of the candidate
	 */
	public Candidate.Builder builder(final VisitRow visit, final InspectionVisitRows rows, final Place place) {
		final var hits = new ArrayList<RuleHit>();
		if (place == null) {
			hits.add(RuleHit.of(PLACE_MISSING, placeMissing));
		}
		if (visit.datum() == null || visit.datum().isAfter(today)) {
			// A visit not yet made may be a planned one, which must not become an inspection that took place
			hits.add(RuleHit.of(VISIT_DATE_INVALID, visit.datum() == null ? "datum saknas" : SourceValues.format(visit.datum())));
		}
		final var content = new VisitContent(visit, rows.kindText(visit.tillsynsart()), time(visit), remarks(rows.remarksOf(visit.tillfalleNr()), rows));
		final var placeName = place == null ? null : place.name();
		final var name = Optional.ofNullable(placeName).orElse(unknownPlace);
		final var title = Texts.title("Tillsynsbesök", name);
		return Candidate.builder(new ItemKey(type.getSource(), TILLSYNSBESOK, String.valueOf(visit.tillfalleNr())), type)
			.placeName(placeName)
			.ruleHits(hits)
			.place(place)
			.title(title)
			.investigation(investigation(title, visit))
			.description(description(name, content))
			.labelPaths(settings.labels())
			.parameters(parameters(content));
	}

	private String description(final String name, final VisitContent content) {
		final var visit = content.visit();
		final var lines = new ArrayList<String>();
		lines.add(Texts.joinWords("Tillsynsbesök på " + name, SourceValues.format(visit.datum())) + ", migrerat från " + type.getSource().getDisplayName() + ".");
		lines.add(line("Tillsynsart", content.kind()));
		lines.add(line("Tid", content.time()));
		if (!content.remarks().isEmpty()) {
			lines.add("Anmärkningar:");
			content.remarks().forEach(remark -> lines.add("- " + remark));
		}
		lines.add(line("Anteckning", visit.fritext()));
		return Texts.join("\n", lines);
	}

	private List<Parameter> parameters(final VisitContent content) {
		final var visit = content.visit();
		final var source = type.getSource();
		return new ParameterList()
			.add(source.name().toLowerCase(Locale.ROOT) + "TillsynsbesokId", "Id för tillsynsbesöket i " + source.getDisplayName(), String.valueOf(visit.tillfalleNr()))
			.add("besoksdatum", "Besöksdatum", SourceValues.format(visit.datum()))
			.add("besokstid", "Tid", content.time())
			.add("tillsynsart", "Tillsynsart", content.kind())
			.add("anmarkningar", "Anmärkningar", content.remarks())
			.add("anteckning", "Anteckning i " + source.getDisplayName(), visit.fritext())
			.add(migrated(today, source))
			.build();
	}

	/**
	 * @return the time of the visit, "hh:mm–hh:mm" when the source has it in a format that can be read, otherwise as
	 *         written; null when the source has none
	 */
	private static String time(final VisitRow visit) {
		final var start = timeOfDay(visit.startTid());
		final var end = timeOfDay(visit.slutTid());
		if (start == null && end == null) {
			return null;
		}
		return end == null ? start : Texts.range(start, end);
	}

	/**
	 * @return the time as "hh:mm", also when AlkT has written it with seconds, otherwise as written
	 */
	private static String timeOfDay(final String value) {
		return SourceValues.parseTime(withoutSeconds(value)).orElse(value);
	}

	private static String withoutSeconds(final String value) {
		return Optional.ofNullable(value).map(written -> SECONDS.matcher(written.strip()).replaceFirst("$1")).orElse(null);
	}

	/**
	 * The investigation of the visit (decision 41): it started on the date of the visit at its start time, or at the start
	 * of that day without one, and was completed on that date at its end time, the day after when the visit went on past
	 * midnight. A visit without an end time has no end.
	 */
	static InvestigationData investigation(final String title, final VisitRow visit) {
		if (visit.datum() == null) {
			return new InvestigationData(title, null, null);
		}
		final var started = at(visit.datum(), visit.startTid()).orElse(visit.datum().atStartOfDay());
		final var completed = at(visit.datum(), visit.slutTid())
			.map(end -> end.isBefore(started) ? end.plusDays(1) : end)
			.orElse(null);
		return new InvestigationData(title, started, completed);
	}

	/**
	 * @return the date at the time, empty when the time cannot be read; 24:00 is the start of the next day
	 */
	private static Optional<LocalDateTime> at(final LocalDate date, final String time) {
		return SourceValues.parseTime(withoutSeconds(time))
			.map(hhmm -> "24:00".equals(hhmm) ? date.plusDays(1).atStartOfDay() : date.atTime(LocalTime.parse(hhmm)));
	}

	/**
	 * @return each remark as "text: note", with what is missing left out
	 */
	private static List<String> remarks(final List<RemarkRow> remarks, final InspectionVisitRows rows) {
		return remarks.stream()
			.map(remark -> Texts.join(": ", Arrays.asList(rows.remarkText(remark.code()), remark.notering())))
			.filter(Objects::nonNull)
			.toList();
	}

	/**
	 * What a visit holds, interpreted once for the description and the parameters.
	 *
	 * @param visit   the row of the visit
	 * @param kind    the text of the kind of inspection
	 * @param time    the time of the visit
	 * @param remarks the remarks as texts
	 */
	private record VisitContent(VisitRow visit, String kind, String time, List<String> remarks) {
	}
}
