package se.sundsvall.aotloader.service.model;

/**
 * What a hit on a review rule does to the migration of the item.
 */
public enum Severity {

	/** The item is not migrated until the data is corrected in the source or the exception list decides it. */
	BLOCKING,

	/**
	 * The item has ended or was a former holder's and is not migrated, unless the exception list says it is to be
	 * migrated.
	 */
	NOT_MIGRATED,

	/** Information for the business; the item is migrated anyway. */
	INFO
}
