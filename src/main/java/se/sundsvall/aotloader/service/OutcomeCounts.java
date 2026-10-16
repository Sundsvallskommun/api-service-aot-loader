package se.sundsvall.aotloader.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import se.sundsvall.aotloader.integration.db.model.RunItemEntity;
import se.sundsvall.aotloader.service.model.Outcome;

/**
 * Counts the rows of a report by outcome.
 */
final class OutcomeCounts {

	private OutcomeCounts() {}

	static Map<Outcome, Long> of(final List<RunItemEntity> lines) {
		return lines.stream().collect(Collectors.groupingBy(RunItemEntity::getOutcome, () -> new EnumMap<>(Outcome.class), Collectors.counting()));
	}
}
