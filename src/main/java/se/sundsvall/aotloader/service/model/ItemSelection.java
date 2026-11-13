package se.sundsvall.aotloader.service.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The items a run or a cleanup is narrowed to, in the order they were asked for; every item of its scope when there are
 * none.
 *
 * @param keys the items
 */
public record ItemSelection(Set<ItemKey> keys) {

	private static final String SEPARATOR = ",";
	private static final int LISTED_KEYS = 20;

	public ItemSelection {
		keys = keys == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(keys));
	}

	/**
	 * @return the selection of every item
	 */
	public static ItemSelection all() {
		return new ItemSelection(Set.of());
	}

	/**
	 * @return the selection of the items, each once
	 */
	public static ItemSelection of(final Collection<ItemKey> keys) {
		return new ItemSelection(new LinkedHashSet<>(keys));
	}

	/**
	 * @param  text the selection as {@link #text()} writes it, null or blank for every item
	 * @return      the selection
	 */
	public static ItemSelection parse(final String text) {
		if (text == null || text.isBlank()) {
			return all();
		}
		return of(Arrays.stream(text.split(SEPARATOR)).map(ItemKey::parse).toList());
	}

	/**
	 * @return the selection as a run keeps it, the keys separated by commas; null for every item
	 */
	public String text() {
		return keys.isEmpty() ? null : keys.stream().map(ItemKey::toString).collect(Collectors.joining(SEPARATOR));
	}

	/**
	 * @return true when the selection is of every item
	 */
	public boolean isAll() {
		return keys.isEmpty();
	}

	/**
	 * @return true when the item is selected
	 */
	public boolean includes(final ItemKey key) {
		return isAll() || keys.contains(key);
	}

	/**
	 * @param  found the items the run found
	 * @return       the selected items not found, in the order they were asked for; none for the selection of every item
	 */
	public List<ItemKey> missing(final Set<ItemKey> found) {
		return keys.stream().filter(key -> !found.contains(key)).toList();
	}

	/**
	 * @return the items as a message lists them: the first 20 separated by commas, and how many more there are
	 */
	public static String listText(final List<ItemKey> keys) {
		final var listed = keys.stream().limit(LISTED_KEYS).map(ItemKey::toString).collect(Collectors.joining(", "));
		return keys.size() > LISTED_KEYS ? "%s och %d till".formatted(listed, keys.size() - LISTED_KEYS) : listed;
	}
}
