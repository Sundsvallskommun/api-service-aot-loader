package se.sundsvall.aotloader.service;

import java.util.List;
import se.sundsvall.aotloader.service.model.Outcome;

/**
 * What a run did with an item, as a row of the report.
 *
 * @param outcome           the outcome
 * @param rules             the codes of the review rules that hit the item
 * @param detail            what the business needs to know, never numbers of persons or companies; the names of files
 *                          that were not uploaded are listed (G14), and may name a person
 * @param errandNumber      the number of the errand in SupportManagement, when there is one
 * @param systemUnavailable true when the item failed because a system it needs could not be reached, which a run stops
 *                          on when it happens to several items in a row
 */
public record ItemResult(Outcome outcome, List<String> rules, String detail, String errandNumber, boolean systemUnavailable) {

	public ItemResult {
		rules = rules == null ? List.of() : List.copyOf(rules);
	}

	/**
	 * The result of an item that did not fail because a system could not be reached.
	 */
	public ItemResult(final Outcome outcome, final List<String> rules, final String detail, final String errandNumber) {
		this(outcome, rules, detail, errandNumber, false);
	}

	/**
	 * @return the result as one of an item that failed because a system it needs could not be reached
	 */
	ItemResult asSystemUnavailable() {
		return new ItemResult(outcome, rules, detail, errandNumber, true);
	}
}
