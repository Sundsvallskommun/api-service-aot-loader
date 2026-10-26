package se.sundsvall.aotloader.service.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * The status of a source item in the status table (section 8). The statuses from TILLSTANDSHAVARE_KLAR to KLAR follow
 * the steps of the flow (section 6), in order. RENSAS is the status of an item whose cleanup has started but not
 * finished (section 10.3): what is left of it in the target systems is removed by the next cleanup, and no run
 * migrates it until then.
 */
public enum ItemStatus {

	GRANSKAS,
	MIGRERAS_INTE,
	VANTAR,
	TILLSTANDSHAVARE_KLAR,
	ARENDE_SKAPAT,
	BILAGOR_KLARA,
	TILLSTAND_SKAPAT,
	BESLUT_KLART,
	RELATION_KLAR,
	KLAR,
	FEL,
	RENSAS;

	private static final Set<ItemStatus> PROGRESS = EnumSet.range(TILLSTANDSHAVARE_KLAR, KLAR);

	/**
	 * @return true for a status that a step of the flow reaches
	 */
	public boolean isProgress() {
		return PROGRESS.contains(this);
	}
}
