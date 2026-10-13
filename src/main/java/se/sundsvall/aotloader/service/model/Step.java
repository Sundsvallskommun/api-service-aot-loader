package se.sundsvall.aotloader.service.model;

/**
 * The steps of the flow per item (section 6), named in the report when a step fails.
 */
public enum Step {

	LAS_OCH_BEDOM("Läs och bedöm"),
	TILLSTANDSHAVARE("Tillståndshavare"),
	ARENDE("Ärende"),
	BILAGOR("Bilagor"),
	TILLSTAND("Tillstånd i PartyAssets"),
	RELATION("Relation till tillståndsärendet"),
	AKTIVERA("Aktivera ärendet");

	private final String displayName;

	Step(final String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
