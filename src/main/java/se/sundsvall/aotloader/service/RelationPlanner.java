package se.sundsvall.aotloader.service;

import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.integration.db.MigrationItemRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationItemEntity;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ItemStatus;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.PermitLink;
import se.sundsvall.aotloader.support.MigrationException;

import static se.sundsvall.aotloader.service.model.ItemStatus.KLAR;
import static se.sundsvall.aotloader.service.model.ItemStatus.MIGRERAS_INTE;

/**
 * Decides the relation from the permit errand of the place to an inspection errand (section 7.7.1).
 */
@Component
class RelationPlanner {

	private final MigrationItemRepository itemRepository;

	RelationPlanner(final MigrationItemRepository itemRepository) {
		this.itemRepository = itemRepository;
	}

	/**
	 * Decides the relation. An inspection errand that concerned the holder of the permit is related to the permit errand
	 * once the permit is migrated, and waits while the permit may still be migrated. One that concerned another holder, or
	 * whose holder or whose permit's holder cannot be seen, gets no relation and does not wait.
	 *
	 * @param  candidate the item
	 * @param  context   the run, which knows what it did with the permit earlier
	 * @param  notes     the report row of the item, which gets why there is no relation
	 * @return           the plan
	 */
	RelationPlan plan(final Candidate candidate, final RunContext context, final ReportNotes notes) {
		final var link = candidate.permitLink();
		if (link == null) {
			return RelationPlan.none();
		}
		final var permitKey = link.permitKey();
		if (permitKey == null) {
			notes.line("Ingen relation: stället har inget gällande tillstånd");
			return RelationPlan.none();
		}
		if (!link.sameHolder()) {
			notes.line(noRelationReason(link));
			return RelationPlan.none();
		}
		final var permitItem = itemRepository.findBySourceAndKindAndSourceId(permitKey.source(), permitKey.kind(), permitKey.sourceId());
		final var permitStatus = permitItem.map(MigrationItemEntity::getStatus).orElse(null);
		final var permitOutcome = context.outcomeOf(permitKey).orElse(null);
		final var permitDone = permitStatus == KLAR || (context.dryRun() && permitOutcome == Outcome.SKULLE_MIGRERAS);
		if (!permitDone && (permitStatus == MIGRERAS_INTE || permitOutcome == Outcome.MIGRERAS_INTE)) {
			notes.line("Ingen relation: tillståndet migreras inte");
			return RelationPlan.none();
		}
		if (!permitDone) {
			final var state = Optional.ofNullable(permitOutcome).map(Outcome::name)
				.or(() -> Optional.ofNullable(permitStatus).map(ItemStatus::name))
				.orElse("inte migrerat");
			return RelationPlan.waitFor("Väntar på tillståndet %s, som är %s".formatted(permitKey, state));
		}
		notes.line("Relation till tillståndsärendet " + permitKey);
		if (context.dryRun()) {
			return RelationPlan.none();
		}
		return RelationPlan.relateTo(permitItem.map(MigrationItemEntity::getErrandId)
			.orElseThrow(() -> new MigrationException("Tillståndet %s saknar ärende-id i statustabellen".formatted(permitKey))));
	}

	private static String noRelationReason(final PermitLink link) {
		if (link.inspectionHolder() == null) {
			return "Ingen relation: det går inte att se vilken tillståndshavare ärendet gällde";
		}
		if (link.permitHolder() == null) {
			return "Ingen relation: det går inte att se vem som har tillståndet";
		}
		return "Ingen relation: ärendet gällde en tidigare tillståndshavare";
	}

	/**
	 * The relation an inspection errand gets, or why it waits.
	 *
	 * @param permitErrandId the permit errand to relate to, null for no relation
	 * @param waitReason     why the item waits, null when it does not
	 */
	record RelationPlan(String permitErrandId, String waitReason) {

		static RelationPlan none() {
			return new RelationPlan(null, null);
		}

		static RelationPlan relateTo(final String permitErrandId) {
			return new RelationPlan(permitErrandId, null);
		}

		static RelationPlan waitFor(final String reason) {
			return new RelationPlan(null, reason);
		}

		boolean waits() {
			return waitReason != null;
		}

		boolean relates() {
			return permitErrandId != null;
		}
	}
}
