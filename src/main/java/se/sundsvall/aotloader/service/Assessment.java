package se.sundsvall.aotloader.service;

import java.util.List;
import java.util.Optional;
import se.sundsvall.aotloader.service.model.ExceptionOutcome;
import se.sundsvall.aotloader.service.model.RuleHit;

import static se.sundsvall.aotloader.service.model.Severity.BLOCKING;
import static se.sundsvall.aotloader.service.model.Severity.NOT_MIGRATED;

/**
 * The assessment of an item against the review rules and the exception list (section 7.1).
 *
 * @param decision what is to be done with the item
 * @param reasons  the hits that decided it, empty when the item is migrated
 * @param note     a note for the report about the exception list, null when there is none
 */
public record Assessment(Decision decision, List<RuleHit> reasons, String note) {

	/**
	 * What is to be done with the item.
	 */
	public enum Decision {
		MIGRATE,
		MIGRATE_AS_EXPIRED,
		NOT_MIGRATED,
		REVIEW
	}

	/**
	 * Assesses the hits of an item.
	 *
	 * @param  hits      the hits on the review rules
	 * @param  exception what the exception list says about the item, empty when it is not there
	 * @return           the assessment
	 */
	public static Assessment of(final List<RuleHit> hits, final Optional<ExceptionOutcome> exception) {
		final var blocking = hits.stream().filter(hit -> hit.rule().getSeverity() == BLOCKING).toList();
		final var notMigrated = hits.stream().filter(hit -> hit.rule().getSeverity() == NOT_MIGRATED).toList();
		if (exception.isPresent()) {
			final var notOverridable = blocking.stream().filter(hit -> !hit.rule().isOverridable()).toList();
			if (!notOverridable.isEmpty()) {
				return new Assessment(Decision.REVIEW, notOverridable, "Undantaget i undantagslistan kan inte tillämpas förrän uppgiften är rättad i källan");
			}
			final var note = "Undantagslistan: " + exception.get();
			return new Assessment(exception.get() == ExceptionOutcome.MIGRERA_SOM_UTGANGET ? Decision.MIGRATE_AS_EXPIRED : Decision.MIGRATE, List.of(), note);
		}
		if (!blocking.isEmpty()) {
			return new Assessment(Decision.REVIEW, blocking, null);
		}
		if (!notMigrated.isEmpty()) {
			return new Assessment(Decision.NOT_MIGRATED, notMigrated, null);
		}
		return new Assessment(Decision.MIGRATE, List.of(), null);
	}
}
