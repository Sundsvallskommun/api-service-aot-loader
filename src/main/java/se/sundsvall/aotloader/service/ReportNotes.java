package se.sundsvall.aotloader.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.support.PiiScrubber;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.Constants.REPORT_TEXT_MAX_LENGTH;

/**
 * Collects the lines and rule codes of the report row of one item.
 */
final class ReportNotes {

	private final List<String> lines = new ArrayList<>();
	private final Set<String> rules = new LinkedHashSet<>();

	void line(final String line) {
		lines.add(line);
	}

	void lineFirst(final String line) {
		lines.addFirst(line);
	}

	void rule(final String code) {
		rules.add(code);
	}

	/**
	 * @return the report row, with numbers and email addresses masked in its detail
	 */
	ItemResult result(final Outcome outcome, final String errandNumber) {
		return new ItemResult(outcome, List.copyOf(rules), lines.isEmpty() ? null : PiiScrubber.scrub(String.join("; ", lines)), errandNumber);
	}

	/**
	 * @return the text as the report and the status table keep it: with numbers and email addresses masked, and no longer
	 *         than their columns
	 */
	static String reportText(final String text) {
		return Texts.truncate(PiiScrubber.scrub(text), REPORT_TEXT_MAX_LENGTH, null);
	}
}
