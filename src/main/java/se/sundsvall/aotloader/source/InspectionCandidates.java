package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.ErrandTypeSettings;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.InvestigationData;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.PermitLink;
import se.sundsvall.aotloader.service.model.Place;
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
	 * @param  permitKey the permit of the place, null when the place has no current permit
	 * @param  decisions the decisions of the errand as texts, in order
	 * @return           the builder of the candidate
	 */
	public Candidate.Builder builder(final InspectionErrand errand, final Place place, final ContactRow applicant, final ContactRow owner, final ItemKey permitKey,
		final List<String> decisions) {
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
			// The errand was started when it was opened and completed when it was closed (decision 41)
			.investigation(new InvestigationData(title, SourceValues.startOfDay(errand.opened()), SourceValues.startOfDay(errand.closed())))
			.description(description(errand, decisions))
			.labelPaths(settings.labels())
			.labelPath(settings.sourceErrandType(errand.typeCode()).orElse(null))
			.externalTag(TAG_DIARY_NUMBER, errand.diaryNumber())
			.parameters(parameters(errand, decisions))
			.permitLink(new PermitLink(permitKey, ownerLegalId, holder == null ? null : holder.legalId()));
	}

	private static String description(final InspectionErrand errand, final List<String> decisions) {
		return Texts.join("\n", Arrays.asList(
			errand.typeText() + ", migrerat från " + errand.source().getDisplayName() + ".",
			line("Diarienummer", errand.diaryNumber()),
			"Öppnat: " + Optional.ofNullable(SourceValues.format(errand.opened())).orElse("okänt"),
			"Avslutat: " + Optional.ofNullable(SourceValues.format(errand.closed())).orElse("inte avslutat"),
			decisions.isEmpty() ? null : "Beslut: " + String.join("; ", decisions)));
	}

	private List<Parameter> parameters(final InspectionErrand errand, final List<String> decisions) {
		final var sourceName = errand.source().getDisplayName();
		return new ParameterList()
			.add(errand.source().name().toLowerCase(Locale.ROOT) + "ArendeId", "Id för ärendet i " + sourceName, String.valueOf(errand.arendeId()))
			.add("arendetyp", "Ärendetyp i " + sourceName, errand.typeText())
			.add("diarienummer", "Diarienummer i " + sourceName, errand.diaryNumber())
			.add("oppnat", "Öppnat", SourceValues.format(errand.opened()))
			.add("avslutat", "Avslutat", SourceValues.format(errand.closed()))
			.add("beslut", "Beslut i " + sourceName, decisions)
			.add(migrated(today, errand.source()))
			.build();
	}

	/**
	 * What an inspection errand was in its source.
	 *
	 * @param source      the source
	 * @param arendeId    the id of the errand in the source
	 * @param typeCode    the code of the errand type, which picks the label of the errand
	 * @param typeText    the text of the errand type
	 * @param diaryNumber the diary number
	 * @param opened      the day the errand was opened
	 * @param closed      the day the errand was closed, null when it is open
	 */
	public record InspectionErrand(Source source, Integer arendeId, String typeCode, String typeText, String diaryNumber, LocalDate opened, LocalDate closed) {
	}
}
