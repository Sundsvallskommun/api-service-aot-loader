package se.sundsvall.aotloader.service.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * The status of a source item in the status table (section 8). The statuses from TILLSTANDSHAVARE_KLAR to KLAR follow
 * the steps of the flow (section 6), in order.
 */
public enum ItemStatus {

	GRANSKAS,
	MIGRERAS_INTE,
	VANTAR,
	TILLSTANDSHAVARE_KLAR,
	ARENDE_SKAPAT,
	BILAGOR_KLARA,
	TILLSTAND_SKAPAT,
	RELATION_KLAR,
	KLAR,
	FEL;

	private static final Set<ItemStatus> PROGRESS = EnumSet.range(TILLSTANDSHAVARE_KLAR, KLAR);

	/**
	 * @return true for a status that a step of the flow reaches
	 */
	public boolean isProgress() {
		return PROGRESS.contains(this);
	}
}
