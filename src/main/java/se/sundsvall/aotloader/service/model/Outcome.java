package se.sundsvall.aotloader.service.model;

/**
 * What a run did with a source item, as the report shows it.
 */
public enum Outcome {

	/** The errand was migrated, or finished, in this run. */
	MIGRERAD,

	/** The errand was migrated in an earlier run and was left as it is. */
	REDAN_MIGRERAD,

	/** A dry run found nothing that stops the migration of the item. */
	SKULLE_MIGRERAS,

	/** The item hits a review rule and waits for the business. */
	GRANSKAS,

	/** The item counts as ended in the source and is not migrated. */
	MIGRERAS_INTE,

	/** The item waits for another item, e.g. an inspection errand for the permit of its place. */
	VANTAR,

	/** Something went wrong; the item is taken up again in the next run. */
	FEL,

	/** The run stopped, or reached its limit, before the item. */
	EJ_KORD
}
