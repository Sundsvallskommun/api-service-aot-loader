package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

/**
 * The texts both sources write the same way into the candidates: the dash of ranges, labelled lines, the parameter that
 * marks an errand as migrated, and the description and parameters of an inspection errand (section 7.7).
 */
public final class CandidateTexts {

	public static final String DASH = "–";
	public static final String UNKNOWN_SERVING_PLACE = "okänt serveringsställe";
	public static final String UNKNOWN_SALES_PLACE = "okänt försäljningsställe";

	private CandidateTexts() {}

	/**
	 * @return "label: value", or null for a blank value
	 */
	public static String line(final String label, final String value) {
		return Texts.isBlank(value) ? null : label + ": " + value;
	}

	/**
	 * @return the parameter that tells when and from where the errand was migrated, e.g. "2026-10-12 från AlkT"
	 */
	public static Parameter migrated(final LocalDate today, final Source source) {
		return new Parameter("migrerad", "Migrerad", List.of(today + " från " + source.getDisplayName()));
	}

	/**
	 * @param  errand    what the inspection errand was in the source
	 * @param  decisions the decisions of the errand, as texts
	 * @return           the description of the inspection errand
	 */
	public static String inspectionDescription(final InspectionErrand errand, final List<String> decisions) {
		return Texts.join("\n", Arrays.asList(
			errand.typeText() + ", migrerat från " + errand.source().getDisplayName() + ".",
			line("Diarienummer", errand.diaryNumber()),
			"Öppnat: " + Optional.ofNullable(SourceValues.format(errand.opened())).orElse("okänt"),
			"Avslutat: " + Optional.ofNullable(SourceValues.format(errand.closed())).orElse("inte avslutat"),
			decisions.isEmpty() ? null : "Beslut: " + String.join("; ", decisions)));
	}

	/**
	 * @param  errand    what the inspection errand was in the source
	 * @param  decisions the decisions of the errand, as texts
	 * @param  today     the day of the migration
	 * @return           the parameters of the inspection errand
	 */
	public static List<Parameter> inspectionParameters(final InspectionErrand errand, final List<String> decisions, final LocalDate today) {
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
	 * @param typeText    the text of the errand type
	 * @param diaryNumber the diary number
	 * @param opened      the day the errand was opened
	 * @param closed      the day the errand was closed, null when it is open
	 */
	public record InspectionErrand(Source source, Integer arendeId, String typeText, String diaryNumber, LocalDate opened, LocalDate closed) {
	}
}
