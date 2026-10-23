package se.sundsvall.aotloader.service;

import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.integration.db.MigrationItemRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationItemEntity;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.ItemStatus;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.PermitLink;
import se.sundsvall.aotloader.service.model.PermitLink.Basis;
import se.sundsvall.aotloader.support.MigrationException;
import se.sundsvall.aotloader.support.SourceValues;

import static se.sundsvall.aotloader.service.model.ItemStatus.KLAR;
import static se.sundsvall.aotloader.service.model.ItemStatus.MIGRERAS_INTE;

/**
 * Decides the relation from the permit errand of the place to an inspection errand or visit (section 7.7.1, decision
 * 61), and from the permit or notification errand to a documentation errand filed under it (decision 71).
 */
@Component
class RelationPlanner {

	private final MigrationItemRepository itemRepository;

	RelationPlanner(final MigrationItemRepository itemRepository) {
		this.itemRepository = itemRepository;
	}

	/**
	 * Decides the relation. An inspection that concerned the holder of the permit, as {@link PermitLink#basis()} tells, is
	 * related to the permit errand once the permit is migrated, and waits while the permit may still be migrated. Any
	 * other gets no relation and does not wait, and the report tells why. A documentation errand is related the same way,
	 * but is not migrated when the errand it is filed under is not. What this run did with the permit decides over what an
	 * earlier run saved, since the permit may since have been let through or stopped for another reason.
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
		final var basis = link.basis();
		if (!basis.relates()) {
			notes.line(reason(link, basis));
			return RelationPlan.none();
		}
		final var permitKey = link.permitKey();
		final var permitItem = itemRepository.findByKey(permitKey);
		final var permitStatus = permitItem.map(MigrationItemEntity::getStatus).orElse(null);
		final var permitOutcome = context.outcomeOf(permitKey).orElse(null);
		final var permitDone = permitStatus == KLAR || (context.dryRun() && permitOutcome == Outcome.SKULLE_MIGRERAS);
		final var permitNotMigrated = permitOutcome == null ? permitStatus == MIGRERAS_INTE : permitOutcome == Outcome.MIGRERAS_INTE;
		if (!permitDone && permitNotMigrated) {
			if (link.filed()) {
				return RelationPlan.notMigrated("Händelsen migreras inte, eftersom %s som den hör till inte migreras (beslut 71)".formatted(itemText(permitKey)));
			}
			notes.line("Ingen relation: tillståndet migreras inte");
			return RelationPlan.none();
		}
		if (!permitDone) {
			final var state = Optional.ofNullable(permitOutcome).map(Outcome::name)
				.or(() -> Optional.ofNullable(permitStatus).map(ItemStatus::name))
				.orElse("inte migrerat");
			return RelationPlan.waitFor("Väntar på %s, som är %s".formatted(itemText(permitKey), state));
		}
		notes.line(reason(link, basis));
		if (context.dryRun()) {
			return RelationPlan.none();
		}
		return RelationPlan.relateTo(permitItem.map(MigrationItemEntity::getErrandId)
			.orElseThrow(() -> new MigrationException("Tillståndet %s saknar ärende-id i statustabellen".formatted(permitKey))));
	}

	/**
	 * @return what the report tells about the relation of the inspection, for the basis of its link
	 */
	private static String reason(final PermitLink link, final Basis basis) {
		final var permit = link.permit();
		return switch (basis) {
			case FILED, SAME_HOLDER -> "Relation till " + errandText(permit.key());
			case AFTER_ISSUE -> "Relation till tillståndsärendet %s, tillsynen gjordes efter att tillståndet utfärdades %s".formatted(permit.key(), SourceValues.format(permit.issued()));
			case NO_PERMIT -> "Ingen relation: stället har inget gällande tillstånd";
			case OTHER_HOLDER -> "Ingen relation: ärendet gällde en annan tillståndshavare än den som har tillståndet";
			case NO_PERMIT_HOLDER -> "Ingen relation: det går inte att se vem som har tillståndet";
			case NO_DATE -> "Ingen relation: tillsynen saknar datum, och det går inte att se vilken tillståndshavare den gällde";
			case NO_ISSUE_DATE -> "Ingen relation: tillståndet saknar utfärdandedatum, och det går inte att se vilken tillståndshavare tillsynen gällde";
			case ISSUED_TO_OTHER -> "Ingen relation: det går inte att se att tillståndet utfärdades till ställets nuvarande ägare, så dagen visar inte vilken tillståndshavare tillsynen gällde";
			case BEFORE_ISSUE -> "Ingen relation: tillsynen gjordes före att tillståndet utfärdades %s, och det går inte att se vilken tillståndshavare den gällde"
				.formatted(SourceValues.format(permit.issued()));
			case AFTER_END -> "Ingen relation: tillsynen gjordes efter att tillståndet upphörde %s".formatted(SourceValues.format(permit.ended()));
		};
	}

	/**
	 * @return the errand of the permit or notification, "tillståndsärendet ALKT/TILLSTAND/1" or "anmälningsärendet
	 *         ALKOL/ANMALAN/1-FOLKOL"
	 */
	static String errandText(final ItemKey key) {
		return (key.kind() == Kind.ANMALAN ? "anmälningsärendet " : "tillståndsärendet ") + key;
	}

	/**
	 * @return the permit or notification, "tillståndet ALKT/TILLSTAND/1" or "anmälan ALKOL/ANMALAN/1-FOLKOL"
	 */
	private static String itemText(final ItemKey key) {
		return (key.kind() == Kind.ANMALAN ? "anmälan " : "tillståndet ") + key;
	}

	/**
	 * The relation an inspection, visit or documentation errand gets, why it waits, or why a documentation errand is not
	 * migrated.
	 *
	 * @param permitErrandId    the permit or notification errand to relate to, null for no relation
	 * @param waitReason        why the item waits, null when it does not
	 * @param notMigratedReason why the item is not migrated, null when it is
	 */
	record RelationPlan(String permitErrandId, String waitReason, String notMigratedReason) {

		static RelationPlan none() {
			return new RelationPlan(null, null, null);
		}

		static RelationPlan relateTo(final String permitErrandId) {
			return new RelationPlan(permitErrandId, null, null);
		}

		static RelationPlan waitFor(final String reason) {
			return new RelationPlan(null, reason, null);
		}

		static RelationPlan notMigrated(final String reason) {
			return new RelationPlan(null, null, reason);
		}

		boolean stops() {
			return notMigratedReason != null;
		}

		boolean waits() {
			return waitReason != null;
		}

		boolean relates() {
			return permitErrandId != null;
		}
	}
}
