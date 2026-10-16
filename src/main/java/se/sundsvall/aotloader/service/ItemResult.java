package se.sundsvall.aotloader.service;

import java.util.List;
import se.sundsvall.aotloader.service.model.Outcome;

/**
 * What a run did with an item, as a row of the report.
 *
 * @param outcome      the outcome
 * @param rules        the codes of the review rules that hit the item
 * @param detail       what the business needs to know, never names or numbers of persons or companies
 * @param errandNumber the number of the errand in SupportManagement, when there is one
 */
public record ItemResult(Outcome outcome, List<String> rules, String detail, String errandNumber) {

	public ItemResult {
		rules = rules == null ? List.of() : List.copyOf(rules);
	}
}
