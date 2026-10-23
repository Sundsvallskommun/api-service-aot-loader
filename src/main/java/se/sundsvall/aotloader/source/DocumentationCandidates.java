package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.ErrandTypeSettings;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
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
import static se.sundsvall.aotloader.service.model.Kind.DOKUMENTATION;
import static se.sundsvall.aotloader.service.model.ReviewRule.FORMER_HOLDER;
import static se.sundsvall.aotloader.source.CandidateTexts.line;
import static se.sundsvall.aotloader.source.CandidateTexts.migrated;

/**
 * Builds the documentation errands of the events of the places that belong to no errand, the same way for both sources
 * (decision 71): every such event with a text or a file becomes an errand of its own, filed under the permit or
 * notification of its place, with the current owner of the place as its holder. An event from the time of a former
 * holder is not migrated (G32, decision 72).
 */
public final class DocumentationCandidates {

	private static final String EVENT = "Händelse";

	private final ErrandType type;
	private final ErrandTypeSettings settings;
	private final String unknownPlace;
	private final LocalDate today;

	/**
	 * @param type         the type of errand the events of the source become
	 * @param properties   the settings of the migration
	 * @param unknownPlace the name in the title of an errand whose place has no name
	 * @param today        the day of the migration
	 */
	public DocumentationCandidates(final ErrandType type, final AotLoaderProperties properties, final String unknownPlace, final LocalDate today) {
		this.type = type;
		this.settings = properties.errandType(type);
		this.unknownPlace = unknownPlace;
		this.today = today;
	}

	/**
	 * @param  text     the text of the event, null when it has none
	 * @param  withFile true when the event has a document or an attachment
	 * @return          true when the event documents something, a text or a file, and becomes an errand; an event with
	 *                  neither tells nothing but its type and day
	 */
	public static boolean documents(final String text, final boolean withFile) {
		return withFile || !Texts.isBlank(text);
	}

	/**
	 * Starts the candidate of the documentation errand of an event with what both sources give it. The source adds the
	 * files.
	 *
	 * @param  event      what the event was in the source
	 * @param  place      the place of the event, null when it has none
	 * @param  owner      the current owner of the place, the holder of the errand; null when the place has none
	 * @param  former     what on the place was a former holder's
	 * @param  filedUnder the permit or notification of the place that the errand is filed under and related from
	 * @return            the builder of the candidate
	 */
	public Candidate.Builder builder(final PlaceEvent event, final Place place, final ContactRow owner, final FormerHolders former, final PlacePermit filedUnder) {
		final var placeName = place == null ? null : place.name();
		final var typeText = Optional.ofNullable(Texts.trimToNull(event.typeText())).orElse(EVENT);
		final var hits = former.isUpTo(event.day())
			? List.of(RuleHit.of(FORMER_HOLDER, "händelsen var %s, och stället hade en tidigare ägare till och med %s".formatted(SourceValues.format(event.day()), SourceValues.format(former.until()))))
			: List.<RuleHit>of();
		return Candidate.builder(new ItemKey(event.source(), DOKUMENTATION, String.valueOf(event.handelseId())), type)
			.placeName(placeName)
			.ruleHits(hits)
			.holder(PartyRows.holder(owner, today))
			.place(place)
			.title(Texts.title(typeText, Optional.ofNullable(placeName).orElse(unknownPlace)))
			.description(description(event, typeText, Optional.ofNullable(placeName).orElse(unknownPlace)))
			.labelPaths(settings.labels())
			.externalTag(TAG_DIARY_NUMBER, event.diaryNumber())
			.parameters(parameters(event, typeText))
			.permitLink(PermitLink.filedUnder(filedUnder));
	}

	/**
	 * @return "Anteckning på Krogen Ett 2024-03-01, migrerad från AlkT, där den inte hörde till något ärende.", its diary
	 *         number, whether it is confidential, and its text
	 */
	private static String description(final PlaceEvent event, final String typeText, final String placeName) {
		final var source = event.source().getDisplayName();
		return Texts.join("\n", Arrays.asList(
			"%s, migrerad från %s, där den inte hörde till något ärende.".formatted(Texts.joinWords(typeText, "på", placeName, SourceValues.format(event.day())), source),
			line("Diarienummer", event.diaryNumber()),
			event.confidential() ? "Händelsen är markerad med sekretess i " + source + "." : null,
			Texts.trimToNull(event.text())));
	}

	private List<Parameter> parameters(final PlaceEvent event, final String typeText) {
		final var sourceName = event.source().getDisplayName();
		return new ParameterList()
			.add(event.source().name().toLowerCase(Locale.ROOT) + "HandelseId", "Id för händelsen i " + sourceName, String.valueOf(event.handelseId()))
			.add("handelsetyp", "Händelsetyp i " + sourceName, typeText)
			.add("handelsedatum", "Händelsedatum", SourceValues.format(event.day()))
			.add(ErrandParameters.DIARY_NUMBER, "Diarienummer i " + sourceName, event.diaryNumber())
			.add("sekretess", "Sekretess", event.confidential() ? "Ja" : null)
			.add(migrated(today, event.source()))
			.build();
	}

	/**
	 * What an event of a place that belongs to no errand was in its source.
	 *
	 * @param source       the source
	 * @param handelseId   the id of the event, HandelseID
	 * @param typeText     the text of the event type, null when it has none
	 * @param tidpunkt     when the event was, null when unknown
	 * @param text         the text of the event, null when it has none
	 * @param confidential true when the event is marked as confidential, Sekretess
	 * @param diaryNumber  the diary number of the event, null when it has none
	 */
	public record PlaceEvent(Source source, Integer handelseId, String typeText, LocalDateTime tidpunkt, String text, boolean confidential, String diaryNumber) {

		/**
		 * @return the day of the event, null when unknown
		 */
		LocalDate day() {
			return tidpunkt == null ? null : tidpunkt.toLocalDate();
		}

		@Override
		public String toString() {
			return "PlaceEvent[source=" + source + ", handelseId=" + handelseId + "]";
		}
	}
}
