package se.sundsvall.aotloader.service.model;

/**
 * A source system that errands are migrated from.
 */
public enum Source {

	ALKT("AlkT"),
	ALKOL("AlkOL");

	private final String displayName;

	Source(final String displayName) {
		this.displayName = displayName;
	}

	/**
	 * @return the name of the source as written in migrated errands and permits, e.g. "AlkT"
	 */
	public String getDisplayName() {
		return displayName;
	}
}
