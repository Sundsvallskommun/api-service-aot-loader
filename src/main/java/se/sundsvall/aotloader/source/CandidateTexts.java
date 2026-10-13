package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

/**
 * The texts both sources write the same way into the candidates: the name of an unknown place, labelled lines, and the
 * parameter that marks an errand as migrated.
 */
public final class CandidateTexts {

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
	 * @return the line "Utfärdat: date (diary number)", with what is missing left out; null when both are
	 */
	public static String issuedLine(final LocalDate issued, final String diaryNumber) {
		return line("Utfärdat", Texts.joinWords(SourceValues.format(issued), Optional.ofNullable(diaryNumber).map(number -> "(" + number + ")").orElse(null)));
	}

	/**
	 * @return the parameter that tells when and from where the errand was migrated, e.g. "2026-10-12 från AlkT"
	 */
	public static Parameter migrated(final LocalDate today, final Source source) {
		return new Parameter("migrerad", "Migrerad", List.of(today + " från " + source.getDisplayName()));
	}
}
