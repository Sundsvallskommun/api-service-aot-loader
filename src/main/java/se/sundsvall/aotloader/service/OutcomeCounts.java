package se.sundsvall.aotloader.service;

import java.util.EnumMap;
import java.util.Map;
import se.sundsvall.aotloader.integration.db.RunItemRepository;
import se.sundsvall.aotloader.service.model.Outcome;

/**
 * Counts the rows of a report by outcome.
 */
final class OutcomeCounts {

	private OutcomeCounts() {}

	/**
	 * @return the number of rows of the run per outcome, counted by the database, in the order of the outcomes
	 */
	static Map<Outcome, Long> of(final RunItemRepository repository, final String runId) {
		final var counts = new EnumMap<Outcome, Long>(Outcome.class);
		repository.countOutcomes(runId).forEach(count -> counts.put(count.getOutcome(), count.getAmount()));
		return counts;
	}
}
