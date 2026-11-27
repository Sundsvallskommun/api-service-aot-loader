package se.sundsvall.aotloader.service.model;

/**
 * What the business has decided for an item in the exception list (section 7.1.1).
 */
public enum ExceptionOutcome {

	/** Migrate the item although review rules hit it. */
	MIGRERA,

	/** Migrate the item, with the post in PartyAssets as EXPIRED. */
	MIGRERA_SOM_UTGANGET
}
