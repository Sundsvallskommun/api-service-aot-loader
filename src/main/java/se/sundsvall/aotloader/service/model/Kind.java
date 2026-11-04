package se.sundsvall.aotloader.service.model;

/**
 * The kind of source item that becomes an errand. A run migrates the kinds in this order, since an inspection errand
 * and a documentation errand are related to the permit or notification errand of their place and therefore wait for it.
 */
public enum Kind {

	/** A current permit, with an errand and a permit in PartyAssets. */
	TILLSTAND,

	/** A notification of a product on a sales place, with an errand and a post in PartyAssets. */
	ANMALAN,

	/** An inspection errand, with an errand only. */
	TILLSYN,

	/** An inspection visit to a place, with an errand only. */
	TILLSYNSBESOK,

	/** An event of a place that belongs to no errand, which becomes a documentation errand (decision 71). */
	DOKUMENTATION;

	/**
	 * @return true for the kinds of inspection, whose holder gets the role of the inspection holder (section 7.7)
	 */
	public boolean isInspection() {
		return this == TILLSYN || this == TILLSYNSBESOK;
	}

	/**
	 * @return true for the kinds whose holder is the one the errand concerned rather than the holder of a permit or a
	 *         notification: the inspections and the documentation (sections 7.7 and 7.8). That holder gets the role of the
	 *         inspection holder, and one that Party does not know is only noted.
	 */
	public boolean hasConcernedHolder() {
		return isInspection() || this == DOKUMENTATION;
	}
}
