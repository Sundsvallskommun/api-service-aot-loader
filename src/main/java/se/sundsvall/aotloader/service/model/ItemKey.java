package se.sundsvall.aotloader.service.model;

import static java.util.Objects.requireNonNull;

/**
 * The identity of a source item: its source, its kind and its id in the source. The id is ObjektID in AlkT and FSID in
 * AlkOL for permits, FSID and product for notifications, ArendeID for inspection errands and TillsynsTillfalleNr for
 * inspection visits.
 *
 * @param source   the source system
 * @param kind     the kind of item
 * @param sourceId the id of the item in the source
 */
public record ItemKey(Source source, Kind kind, String sourceId) {

	public ItemKey {
		requireNonNull(source, "source");
		requireNonNull(kind, "kind");
		requireNonNull(sourceId, "sourceId");
	}

	/**
	 * @return the value of the external tag that marks the migrated errand, unique for every source item, e.g.
	 *         "ALKT-TILLSTAND-123"
	 */
	public String migrationId() {
		return "%s-%s-%s".formatted(source, kind, sourceId);
	}

	@Override
	public String toString() {
		return "%s/%s/%s".formatted(source, kind, sourceId);
	}
}
