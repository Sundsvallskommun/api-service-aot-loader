package se.sundsvall.aotloader.service.model;

/**
 * The subtypes of a serving permit (section 7.3.4), each with a label of its own in the settings of serving permits.
 */
public enum ServingPermitSubtype {

	TEMPORARY_SERVING_PUBLIC,
	TEMPORARY_SERVING_PRIVATE,
	PERMANENT_SERVING,
	PERMANENT_CATERING,
	FARM_SALES,
	TASTING
}
