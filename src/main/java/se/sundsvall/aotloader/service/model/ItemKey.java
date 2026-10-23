package se.sundsvall.aotloader.service.model;

import java.util.Locale;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * The identity of a source item: its source, its kind and its id in the source. The id is ObjektID in AlkT and FSID in
 * AlkOL for permits, FSID and product for notifications, ArendeID for inspection errands, TillsynsTillfalleNr for
 * inspection visits and HandelseID for documentation errands.
 *
 * @param source   the source system
 * @param kind     the kind of item
 * @param sourceId the id of the item in the source
 */
public record ItemKey(Source source, Kind kind, String sourceId) {

	// A comma would split the key where a run keeps its selection (ItemSelection)
	private static final Pattern ID = Pattern.compile("[^\\s,/]+");

	public ItemKey {
		requireNonNull(source, "source");
		requireNonNull(kind, "kind");
		requireNonNull(sourceId, "sourceId");
	}

	/**
	 * Reads a key as {@link #toString()} writes it, e.g. "ALKT/TILLSTAND/123", with the source and the kind in any case.
	 *
	 * @param  text                     the key
	 * @return                          the key
	 * @throws IllegalArgumentException when the text is not a source, a kind and an id separated by slashes, or the id has
	 *                                  a space or a comma
	 */
	public static ItemKey parse(final String text) {
		final var parts = requireNonNull(text, "text").strip().split("/", -1);
		if (parts.length != 3 || !ID.matcher(parts[2]).matches()) {
			throw new IllegalArgumentException("Not an item key: " + text);
		}
		return new ItemKey(Source.valueOf(parts[0].toUpperCase(Locale.ROOT)), Kind.valueOf(parts[1].toUpperCase(Locale.ROOT)), parts[2]);
	}

	/**
	 * @return the value of the external tag that marks the migrated errand, unique for every source item, e.g.
	 *         "ALKT-TILLSTAND-123"
	 */
	public String migrationId() {
		// The names, not toString, since the value is a key the target systems keep
		return "%s-%s-%s".formatted(source.name(), kind.name(), sourceId);
	}

	@Override
	public String toString() {
		return "%s/%s/%s".formatted(source, kind, sourceId);
	}
}
