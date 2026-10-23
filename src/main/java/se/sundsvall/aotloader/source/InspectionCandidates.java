package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.ErrandTypeSettings;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.InvestigationData;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.PermitLink;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.service.model.PlacePermit;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.Constants.TAG_DIARY_NUMBER;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYN;
import static se.sundsvall.aotloader.service.model.ReviewRule.ERRAND_OPEN;
import static se.sundsvall.aotloader.service.model.ReviewRule.PLACE_MISSING;
import static se.sundsvall.aotloader.source.CandidateTexts.line;
import static se.sundsvall.aotloader.source.CandidateTexts.migrated;

/**
 * Builds the candidates of the inspection errands of a source, the same way for both sources (section 7.7): which
 * errands are migrated, and their rules, holder, relation to the permit errand, title, description, labels and
 * parameters.
 */
public final class InspectionCandidates {

	private final ErrandType type;
	private final ErrandTypeSettings settings;
	private final String unknownPlace;
	private final String placeMissing;
	private final LocalDate today;

	/**
	 * @param type         the type of errand the inspection errands of the source become
	 * @param properties   the settings of the migration
	 * @param unknownPlace the name in the title of an errand without a place
	 * @param placeMissing the detail of rule G1 for an errand without a place
	 * @param today        the day of the migration
	 */
	public InspectionCandidates(final ErrandType type, final AotLoaderProperties properties, final String unknownPlace, final String placeMissing, final LocalDate today) {
		this.type = type;
		this.settings = properties.errandType(type);
		this.unknownPlace = unknownPlace;
		this.placeMissing = placeMissing;
		this.today = today;
	}

	/**
	 * @param  typeCode       the errand type in the source
	 * @param  placeHasPermit true when the place of the errand has a current permit
	 * @return                true when the errand is to be migrated: its type is an inspection errand in the settings, and
	 *                        its place has a current permit when the settings take only those
	 */
	public boolean selects(final String typeCode, final boolean placeHasPermit) {
		return settings.sourceErrandType(typeCode).isPresent() && (placeHasPermit || !settings.onlyPlacesWithPermit());
	}

	/**
	 * Starts the candidate of an inspection errand with what both sources give it. The source adds the rest, such as the
	 * files.
	 *
	 * @param  errand    what the errand was in the source
	 * @param  place     the place of the errand, null when it has none
	 * @param  applicant the applicant of the errand, who is the holder it concerned; null when it has none
	 * @param  owner     the owner of the place, null when it has none
	 * @param  permit    the current permit of the place, null when the place has none
	 * @param  decisions the decisions of the errand as texts, in order
	 * @param  texts     the texts of the errand's investigation (decision 44)
	 * @return           the builder of the candidate
	 */
	public Candidate.Builder builder(final InspectionErrand errand, final Place place, final ContactRow applicant, final ContactRow owner, final PlacePermit permit,
		final List<String> decisions, final InvestigationTexts texts) {
		final var hits = new ArrayList<RuleHit>();
		if (place == null) {
			hits.add(RuleHit.of(PLACE_MISSING, placeMissing));
		}
		if (errand.closed() == null) {
			hits.add(RuleHit.of(ERRAND_OPEN));
		}
		final var holder = PartyRows.holder(applicant, today);
		final var ownerLegalId = Optional.ofNullable(PartyRows.holder(owner, today)).map(Holder::legalId).orElse(null);
		final var placeName = place == null ? null : place.name();
		final var title = Texts.title(errand.typeText(), Optional.ofNullable(placeName).orElse(unknownPlace));
		return Candidate.builder(new ItemKey(errand.source(), TILLSYN, String.valueOf(errand.arendeId())), type)
			.placeName(placeName)
			.ruleHits(hits)
			.holder(holder)
			.place(place)
			.title(title)
			.investigation(investigation(title, errand, texts))
			.description(description(errand, decisions))
			.labelPaths(settings.labels())
			.labelPath(settings.sourceErrandType(errand.typeCode()).orElse(null))
			.externalTag(TAG_DIARY_NUMBER, errand.diaryNumber())
			.parameters(parameters(errand, decisions))
			.permitLink(new PermitLink(permit, ownerLegalId, holder == null ? null : holder.legalId(), Optional.ofNullable(errand.opened()).orElse(errand.registered())));
	}

	/**
	 * The investigation of an inspection errand (decisions 41 and 59): it started when the errand was opened, or when it
	 * was registered without a day of opening but not after it was closed, and was completed when it was closed, at the
	 * start of those days. An errand with only an end started then, so that both are set whenever the source has a date,
	 * and an end before the start is the start. An errand still open in the source, which Ä1 keeps from being migrated,
	 * is completed at its latest event or decision, otherwise when it started.
	 */
	static InvestigationData investigation(final String title, final InspectionErrand errand, final InvestigationTexts texts) {
		final var closed = Optional.ofNullable(SourceValues.startOfDay(errand.closed())).orElse(errand.lastActivity());
		final var started = Optional.ofNullable(SourceValues.startOfDay(errand.opened()))
			.or(() -> Optional.ofNullable(SourceValues.startOfDay(errand.registered())).map(registered -> closed != null && closed.isBefore(registered) ? closed : registered))
			.orElse(closed);
		final var completed = Optional.ofNullable(closed).filter(end -> !end.isBefore(started)).orElse(started);
		return new InvestigationData(title, started, completed, texts);
	}

	private static String description(final InspectionErrand errand, final List<String> decisions) {
		return Texts.join("\n", Arrays.asList(
			errand.typeText() + ", migrerat från " + errand.source().getDisplayName() + ".",
			line("Diarienummer", errand.diaryNumber()),
			"Öppnat: " + Optional.ofNullable(SourceValues.format(errand.opened())).orElse("okänt"),
			"Avslutat: " + Optional.ofNullable(SourceValues.format(errand.closed())).orElse("inte avslutat"),
			line("Handläggare", Texts.join(", ", errand.handlers())),
			decisions.isEmpty() ? null : "Beslut: " + String.join("; ", decisions)));
	}

	private List<Parameter> parameters(final InspectionErrand errand, final List<String> decisions) {
		final var sourceName = errand.source().getDisplayName();
		return new ParameterList()
			.add(errand.source().name().toLowerCase(Locale.ROOT) + "ArendeId", "Id för ärendet i " + sourceName, String.valueOf(errand.arendeId()))
			.add("arendetyp", "Ärendetyp i " + sourceName, errand.typeText())
			.add(ErrandParameters.DIARY_NUMBER, "Diarienummer i " + sourceName, errand.diaryNumber())
			.add("oppnat", "Öppnat", SourceValues.format(errand.opened()))
			.add("avslutat", "Avslutat", SourceValues.format(errand.closed()))
			.add(ErrandParameters.HANDLERS, "Handläggare i " + sourceName, errand.handlers())
			.add("beslut", "Beslut i " + sourceName, decisions)
			.add(migrated(today, errand.source()))
			.build();
	}

	/**
	 * What an inspection errand was in its source.
	 *
	 * @param source       the source
	 * @param arendeId     the id of the errand in the source
	 * @param typeCode     the code of the errand type, which picks the label of the errand
	 * @param typeText     the text of the errand type
	 * @param diaryNumber  the diary number
	 * @param opened       the day the errand was opened
	 * @param closed       the day the errand was closed, null when it is open
	 * @param handlers     the names of the handlers of the errand, or their codes when no name is found (decision 51)
	 * @param registered   the day the errand was registered, UpplagdDatum
	 * @param lastActivity the time of the errand's latest event or decision, null when it has none
	 */
	public record InspectionErrand(Source source, Integer arendeId, String typeCode, String typeText, String diaryNumber, LocalDate opened, LocalDate closed,
		List<String> handlers, LocalDate registered, LocalDateTime lastActivity) {

		public InspectionErrand {
			handlers = handlers == null ? List.of() : List.copyOf(handlers);
		}

		/**
		 * @param  source   the source
		 * @param  errand   the row of the errand
		 * @param  typeText the text of its errand type
		 * @param  handlers the names of its handlers, or their codes when no name is found
		 * @param  activity the times of its events and decisions, null for one without a time
		 * @return          what the errand was in its source, last worked on at the latest of the times
		 */
		public static InspectionErrand of(final Source source, final SourceErrand errand, final String typeText, final List<String> handlers, final Stream<LocalDateTime> activity) {
			return new InspectionErrand(source, errand.arendeId(), errand.arendeTyp(), typeText, errand.diarieNr(), errand.oppnad(), errand.avslutad(), handlers, errand.upplagd(),
				activity.filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null));
		}

		@Override
		public String toString() {
			return "InspectionErrand[source=" + source + ", arendeId=" + arendeId + ", typeCode=" + typeCode + "]";
		}
	}
}
