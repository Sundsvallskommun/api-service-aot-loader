package se.sundsvall.aotloader.service.model;

import static se.sundsvall.aotloader.Constants.LABEL_SERVING_PERMIT;

/**
 * The subtypes of a serving permit (section 7.3.4). Each is a label under the label of serving permits.
 */
public enum ServingPermitSubtype {

	TEMPORARY_SERVING_PUBLIC,
	TEMPORARY_SERVING_PRIVATE,
	PERMANENT_SERVING,
	PERMANENT_CATERING,
	FARM_SALES,
	TASTING;

	/**
	 * @return the resource path of the label of the subtype
	 */
	public String getLabelPath() {
		return LABEL_SERVING_PERMIT + "/" + name();
	}
}
