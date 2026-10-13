package se.sundsvall.aotloader.service.model;

/**
 * The kind of source item that becomes an errand. A run migrates the kinds in this order, since an inspection errand
 * is related to the permit errand of its place and therefore waits for it.
 */
public enum Kind {

	/** A current permit, with an errand and a permit in PartyAssets. */
	TILLSTAND,

	/** A notification of a product on a sales place, with an errand and a post in PartyAssets. */
	ANMALAN,

	/** An inspection errand, with an errand only. */
	TILLSYN
}
