package se.sundsvall.aotloader.service.model;

import static java.util.Objects.requireNonNull;

/**
 * A hit on a review rule.
 *
 * @param rule   the rule
 * @param detail what the rule found, never names or numbers of persons or companies, may be null
 */
public record RuleHit(ReviewRule rule, String detail) {

	public RuleHit {
		requireNonNull(rule, "rule");
	}

	public static RuleHit of(final ReviewRule rule) {
		return new RuleHit(rule, null);
	}

	public static RuleHit of(final ReviewRule rule, final String detail) {
		return new RuleHit(rule, detail);
	}

	/**
	 * @return the code and the description of the rule, followed by the detail when there is one, e.g. "G2 Tillståndets
	 *         ägare skiljer sig från serveringsställets nuvarande ägare: AgarID 1, 2"
	 */
	public String toReportText() {
		return detail == null || detail.isBlank() ? rule.getCode() + " " + rule.getDescription() : rule.getCode() + " " + rule.getDescription() + ": " + detail;
	}
}
